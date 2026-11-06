// Read-only bridge to the existing machine DAC installation. No key is exported,
// configuration changed, service installed/stopped or arbitrary command executed.
using System;
using System.IO;
using System.Security.AccessControl;
using System.Security.Principal;
using System.ServiceProcess;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using Microsoft.Win32;
using Microsoft.Win32.SafeHandles;

namespace Npdms.Dac.Desktop
{
    internal static class ServiceBridge
    {
        private const FileSystemRights Writes = FileSystemRights.WriteData | FileSystemRights.AppendData
            | FileSystemRights.WriteExtendedAttributes | FileSystemRights.WriteAttributes | FileSystemRights.Delete
            | FileSystemRights.DeleteSubdirectoriesAndFiles | FileSystemRights.ChangePermissions | FileSystemRights.TakeOwnership;
        private static bool Trusted(SecurityIdentifier sid, bool service)
        {
            return sid.IsWellKnown(WellKnownSidType.LocalSystemSid) || sid.IsWellKnown(WellKnownSidType.BuiltinAdministratorsSid)
                || sid.Value == "S-1-5-80-956008885-3418522649-1831038044-1853292631-2271478464"
                || (service && sid.IsWellKnown(WellKnownSidType.LocalServiceSid));
        }
        internal static void CheckAcl(FileSystemSecurity security, bool service, bool ancestor = false)
        {
            if (!Trusted((SecurityIdentifier)security.GetOwner(typeof(SecurityIdentifier)), service)) throw new IOException();
            foreach (FileSystemAccessRule rule in security.GetAccessRules(true, true, typeof(SecurityIdentifier)))
                if (rule.AccessControlType == AccessControlType.Allow && (rule.PropagationFlags & PropagationFlags.InheritOnly) == 0
                    && (rule.FileSystemRights & (ancestor ? Writes & ~(FileSystemRights.WriteData | FileSystemRights.AppendData) : Writes)) != 0
                    && !Trusted((SecurityIdentifier)rule.IdentityReference, service)) throw new IOException();
        }
        private static void NoLinks(string path)
        {
            for (string current = Path.GetFullPath(path); current != null; current = Path.GetDirectoryName(current))
                if ((File.GetAttributes(current) & FileAttributes.ReparsePoint) != 0) throw new IOException();
        }
        private static void DirectoryCheck(string path, bool service)
        {
            NoLinks(path);
            CheckAcl(Directory.GetAccessControl(path), service);
            // Parent directories must not allow replacement of this protected subtree.
            // Volume root create-child rights do not grant replacement of existing children.
            for (string parent = Path.GetDirectoryName(path); parent != null && parent != Path.GetPathRoot(parent); parent = Path.GetDirectoryName(parent))
                CheckAcl(Directory.GetAccessControl(parent), false, true);
        }
        [StructLayout(LayoutKind.Sequential)]
        private struct FileInfo
        {
            public uint Attributes;
            public System.Runtime.InteropServices.ComTypes.FILETIME Created, Accessed, Written;
            public uint Volume, SizeHigh, SizeLow, Links, IndexHigh, IndexLow;
        }
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool GetFileInformationByHandle(SafeFileHandle handle, out FileInfo info);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern uint GetFinalPathNameByHandle(SafeFileHandle handle, System.Text.StringBuilder name, uint size, uint flags);
        private static FileStream ProtectedFile(string path, bool service, long maximum, bool publicData = false)
        {
            // Public config/endpoint are opened directly using their existing read ACL.
            // The private root is never traversed for ACL reads. Runtime trust additionally
            // requires SCM + live process and listener ownership below; no fallback path.
            if (!publicData) NoLinks(path);
            var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
            try
            {
                FileInfo info;
                if (!GetFileInformationByHandle(stream.SafeFileHandle, out info) || info.Links != 1
                    || (info.Attributes & (uint)(FileAttributes.ReparsePoint | FileAttributes.Directory)) != 0
                    || stream.Length < 1 || stream.Length > maximum) throw new IOException();
                var finalPath = new System.Text.StringBuilder(32768);
                uint length = GetFinalPathNameByHandle(stream.SafeFileHandle, finalPath, (uint)finalPath.Capacity, 0);
                if (length == 0 || length >= finalPath.Capacity
                    || !String.Equals(finalPath.ToString(), @"\\?\" + Path.GetFullPath(path), StringComparison.OrdinalIgnoreCase)) throw new IOException();
                CheckAcl(stream.GetAccessControl(), service);
                return stream;
            }
            catch { stream.Dispose(); throw; }
        }
        private static string CheckService()
        {
            using (var key = Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Services\NpdmsDac", false))
            {
                if (key == null) throw new IOException();
                var acl = key.GetAccessControl();
                if (!Trusted((SecurityIdentifier)acl.GetOwner(typeof(SecurityIdentifier)), false)) throw new IOException();
                foreach (RegistryAccessRule rule in acl.GetAccessRules(true, true, typeof(SecurityIdentifier)))
                    if (rule.AccessControlType == AccessControlType.Allow
                        && (rule.RegistryRights & (RegistryRights.SetValue | RegistryRights.CreateSubKey | RegistryRights.Delete
                            | RegistryRights.ChangePermissions | RegistryRights.TakeOwnership)) != 0
                        && !Trusted((SecurityIdentifier)rule.IdentityReference, false)) throw new IOException();
                string image = key.GetValue("ImagePath", null, RegistryValueOptions.DoNotExpandEnvironmentNames) as string;
                if (image != null && image.StartsWith("\"", StringComparison.Ordinal) && image.EndsWith("\"", StringComparison.Ordinal))
                    image = image.Substring(1, image.Length - 2);
                if (!ClientConfiguration.IsOwnedServiceExecutable(image)
                    || !String.Equals(key.GetValue("ObjectName") as string, @"NT AUTHORITY\LocalService", StringComparison.OrdinalIgnoreCase)) throw new IOException();
                string install = Path.GetDirectoryName(image);
                DirectoryCheck(install, false);
                using (ProtectedFile(image, false, 100 * 1024 * 1024)) { }
                using (ProtectedFile(Path.Combine(install, "SdpDacHost.exe"), false, 100 * 1024 * 1024)) { }
                using (ProtectedFile(Path.ChangeExtension(image, ".xml"), false, 1024 * 1024)) { }
                DirectoryCheck(Path.Combine(install, "runtime", "bin"), false);
                using (ProtectedFile(Path.Combine(install, "runtime", "bin", "java.exe"), false, 100 * 1024 * 1024)) { }
                return image;
            }
        }
        private static int Main(string[] args)
        {
            try
            {
                if (Environment.OSVersion.Platform != PlatformID.Win32NT || !Environment.Is64BitProcess
                    || args.Length != 1 || (args[0] != "snapshot" && args[0] != "start")) return 2;
                string serviceImage = CheckService();
                using (var configurationFile = ProtectedFile(Path.Combine(ClientConfiguration.DataRoot, "client.xml"), false, 16384, true))
                using (var service = new ServiceController(ClientConfiguration.ServiceName))
                {
                    // Same original constructor/parser rules, but parse the authenticated
                    // open handle instead of reopening a path that might have been replaced.
                    ClientConfiguration configuration;
                    using (var reader = System.Xml.XmlReader.Create(configurationFile,
                        new System.Xml.XmlReaderSettings { DtdProcessing = System.Xml.DtdProcessing.Prohibit, XmlResolver = null }))
                    {
                        var doc = System.Xml.Linq.XDocument.Load(reader);
                        configuration = new ClientConfiguration((string)doc.Root.Element("serverUrl"), (string)doc.Root.Element("webOrigin"));
                    }
                    if (args[0] == "start")
                    {
                        if (service.Status == ServiceControllerStatus.Stopped) service.Start();
                        service.WaitForStatus(ServiceControllerStatus.Running, TimeSpan.FromSeconds(30));
                    }
                    var result = new Dictionary<string, object> {
                        {"serviceState", service.Status.ToString()}, {"serverUrl", configuration.ServerUrl},
                        {"webOrigin", configuration.WebOrigin}, {"metadata", null}, {"ownershipVerified", false}
                    };
                    if (service.Status == ServiceControllerStatus.Running)
                    {
                        string runtime = ClientConfiguration.RuntimeDirectory(ClientConfiguration.DataRoot);
                        string file = Path.Combine(runtime, "endpoint.properties");
                        // An absent endpoint while Java starts is not a usable identity.
                        if (File.Exists(file)) using (var stream = ProtectedFile(file, true, 4096, true))
                        using (var reader = new StreamReader(stream, new System.Text.UTF8Encoding(false, true)))
                        {
                            string metadata = reader.ReadToEnd();
                            var fields = System.Text.RegularExpressions.Regex.Match(metadata,
                                "\\Aport=(828[0-9])\\npid=([1-9][0-9]*)\\npublicKey=([a-f0-9]{64})\\ninstanceId=([a-f0-9-]{36})\\n\\z");
                            uint pid; int port;
                            if (!fields.Success || !UInt32.TryParse(fields.Groups[2].Value, out pid)
                                || !Int32.TryParse(fields.Groups[1].Value, out port)) throw new IOException();
                            ServiceOwnership.Verify(ClientConfiguration.ServiceName, serviceImage, pid, port);
                            result["metadata"] = metadata;
                            result["ownershipVerified"] = true;
                        }
                    }
                    Console.WriteLine(new System.Web.Script.Serialization.JavaScriptSerializer().Serialize(result));
                    return 0;
                }
            }
            catch (UnauthorizedAccessException) { Console.Error.WriteLine("DAC_WINDOWS_ACCESS_DENIED"); return 1; }
            catch (System.Security.SecurityException) { Console.Error.WriteLine("DAC_WINDOWS_ACCESS_DENIED"); return 1; }
            catch { Console.Error.WriteLine("DAC_WINDOWS_SERVICE_UNTRUSTED"); return 1; }
        }
    }
}
