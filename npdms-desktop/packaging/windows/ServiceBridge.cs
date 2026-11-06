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
        private static FileStream ProtectedFile(string path, bool service, long maximum)
        {
            NoLinks(path);
            var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
            try
            {
                FileInfo info;
                if (!GetFileInformationByHandle(stream.SafeFileHandle, out info) || info.Links != 1
                    || (info.Attributes & (uint)(FileAttributes.ReparsePoint | FileAttributes.Directory)) != 0
                    || stream.Length < 1 || stream.Length > maximum) throw new IOException();
                CheckAcl(stream.GetAccessControl(), service);
                return stream;
            }
            catch { stream.Dispose(); throw; }
        }
        private static void CheckService()
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
            }
        }
        private static int Main(string[] args)
        {
            try
            {
                if (Environment.OSVersion.Platform != PlatformID.Win32NT || !Environment.Is64BitProcess
                    || args.Length != 1 || (args[0] != "snapshot" && args[0] != "start")) return 2;
                CheckService();
                DirectoryCheck(ClientConfiguration.DataRoot, true);
                using (ProtectedFile(Path.Combine(ClientConfiguration.DataRoot, "client.xml"), false, 16384))
                using (var service = new ServiceController(ClientConfiguration.ServiceName))
                {
                    var configuration = ClientConfiguration.Load(ClientConfiguration.DataRoot);
                    if (args[0] == "start")
                    {
                        if (service.Status == ServiceControllerStatus.Stopped) service.Start();
                        service.WaitForStatus(ServiceControllerStatus.Running, TimeSpan.FromSeconds(30));
                    }
                    var result = new Dictionary<string, object> {
                        {"serviceState", service.Status.ToString()}, {"serverUrl", configuration.ServerUrl},
                        {"webOrigin", configuration.WebOrigin}, {"metadata", null}
                    };
                    if (service.Status == ServiceControllerStatus.Running)
                    {
                        string runtime = ClientConfiguration.RuntimeDirectory(ClientConfiguration.DataRoot);
                        DirectoryCheck(runtime, true);
                        string file = Path.Combine(runtime, "endpoint.properties");
                        // An absent endpoint while Java starts is not a usable identity.
                        if (File.Exists(file)) using (var stream = ProtectedFile(file, true, 4096))
                        using (var reader = new StreamReader(stream, new System.Text.UTF8Encoding(false, true)))
                            result["metadata"] = reader.ReadToEnd();
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
