using System;
using System.IO;
using System.Security.AccessControl;
using System.Security.Cryptography;
using System.Security.Principal;
using System.Text;
using System.Xml.Linq;

namespace Npdms.Dac.Desktop
{
    internal sealed class ClientConfiguration
    {
        internal const string ServiceName = "NpdmsDac";
        internal const int Port = 8280;
        internal const int LastPort = 8289;
        internal static readonly string DataRoot = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "NPDMS", "DAC");
        internal readonly string ServerUrl;
        internal readonly string WebOrigin;

        internal ClientConfiguration(string serverUrl, string webOrigin)
        {
            ServerUrl = ValidateUrl(serverUrl, false);
            WebOrigin = ValidateUrl(String.IsNullOrWhiteSpace(webOrigin) ? new Uri(ServerUrl).GetLeftPart(UriPartial.Authority) : webOrigin, true);
        }

        internal static string ValidateUrl(string value, bool originOnly)
        {
            string field = originOnly ? "NPDMS 网页地址" : "NPDMS 后端地址";
            if (String.IsNullOrWhiteSpace(value))
                throw new ArgumentException(field + "不能为空。");
            if (value.IndexOf("://", StringComparison.Ordinal) < 0)
                throw new ArgumentException(field + "缺少协议前缀，请填写完整的 HTTP 或 HTTPS 地址，例如 http://10.210.0.11:59191。");
            Uri uri;
            if (value.Length > 2048 || value.IndexOfAny(new[] { '\r', '\n', '"', '\\', '*' }) >= 0
                || !Uri.TryCreate(value.Trim(), UriKind.Absolute, out uri)
                || !String.IsNullOrEmpty(uri.UserInfo) || !String.IsNullOrEmpty(uri.Query)
                || !String.IsNullOrEmpty(uri.Fragment) || String.IsNullOrEmpty(uri.Host)
                || uri.Port < 1 || uri.Port > 65535 || !(uri.Scheme == "https" || uri.Scheme == "http"))
                throw new ArgumentException(field + "格式无效，请检查协议、主机和端口，不要包含用户名、密码、查询参数或片段。");
            if (originOnly && uri.AbsolutePath != "/")
                throw new ArgumentException("网页来源只填写协议、主机和端口，不包含页面路径。");
            return (originOnly ? uri.GetLeftPart(UriPartial.Authority) : uri.AbsoluteUri.TrimEnd('/'));
        }

        internal static bool IsStartUri(string value)
        {
            // Exact action only: no server changes, credentials, commands or arbitrary executable paths.
            return String.Equals(value, "npdms-dac://start/", StringComparison.OrdinalIgnoreCase);
        }

        internal static string[] ParseConnectUri(string value)
        {
            // No callback URL, command, credentials, fragment or additional arguments are accepted.
            var match = System.Text.RegularExpressions.Regex.Match(value ?? "",
                "\\Anpdms-dac://connect/([a-f0-9]{32})\\?challenge=([a-f0-9]{64})&tenant=([1-9][0-9]{0,17})\\z");
            if (!match.Success) throw new ArgumentException("本机连接链接无效，请在 NPDMS 网页重新点击连接。");
            return new[] { match.Groups[1].Value, match.Groups[2].Value, match.Groups[3].Value };
        }

        internal string EnvironmentDirectory(string root)
        {
            using (var digest = SHA256.Create())
            {
                string id = BitConverter.ToString(digest.ComputeHash(Encoding.UTF8.GetBytes(ServerUrl))).Replace("-", "").ToLowerInvariant();
                return Path.Combine(root, "environments", id);
            }
        }

        internal static ClientConfiguration Load(string root)
        {
            var settings = new System.Xml.XmlReaderSettings { DtdProcessing = System.Xml.DtdProcessing.Prohibit, XmlResolver = null };
            using (var reader = System.Xml.XmlReader.Create(Path.Combine(root, "client.xml"), settings))
            {
                var doc = XDocument.Load(reader);
                return new ClientConfiguration((string)doc.Root.Element("serverUrl"), (string)doc.Root.Element("webOrigin"));
            }
        }

        internal void Save(string root)
        {
            SecureDirectory(root, false);
            SecureDirectory(EnvironmentDirectory(root), true);
            SecureDirectory(Path.Combine(root, "logs"), true);
            SecureRuntimeDirectory(root);
            var doc = new XDocument(new XElement("dac", new XElement("serverUrl", ServerUrl), new XElement("webOrigin", WebOrigin)));
            string target = Path.Combine(root, "client.xml");
            string pending = target + ".new";
            doc.Save(pending);
            if (File.Exists(target)) File.Replace(pending, target, null);
            else File.Move(pending, target);
            SecureFile(target, true);
            EnsureMasterKey(root);
        }

        internal static void EnsureMasterKey(string root)
        {
            string path = Path.Combine(root, "credentials.key.dpapi");
            if (File.Exists(path)) { SecureFile(path, false); return; }
            byte[] key = new byte[32];
            try
            {
                using (var random = RandomNumberGenerator.Create()) random.GetBytes(key);
                byte[] encrypted = ProtectedData.Protect(key, null, DataProtectionScope.LocalMachine);
                using (var file = new FileStream(path, FileMode.CreateNew, FileAccess.Write, FileShare.None))
                    file.Write(encrypted, 0, encrypted.Length);
                SecureFile(path, false);
            }
            finally { Array.Clear(key, 0, key.Length); }
        }

        internal static byte[] ReadMasterKey(string root)
        {
            return ProtectedData.Unprotect(File.ReadAllBytes(Path.Combine(root, "credentials.key.dpapi")), null, DataProtectionScope.LocalMachine);
        }

        internal static string RuntimeDirectory(string root) { return Path.Combine(root, "runtime"); }

        internal static void SecureRuntimeDirectory(string root)
        {
            string path = RuntimeDirectory(root);
            SecureDirectory(path, true);
            var security = Directory.GetAccessControl(path);
            security.AddAccessRule(new FileSystemAccessRule(Sid(WellKnownSidType.BuiltinUsersSid), FileSystemRights.ReadAndExecute,
                InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit, PropagationFlags.None, AccessControlType.Allow));
            Directory.SetAccessControl(path, security);
        }

        internal static void GrantServiceProgramAccess(string root)
        {
            // Program files only: do not broaden access to ProgramData credentials or task data.
            GrantProgramDirectory(root);
            foreach (string file in Directory.GetFiles(root))
            {
                if ((File.GetAttributes(file) & FileAttributes.ReparsePoint) != 0) throw new IOException("Unexpected program link");
                var security = File.GetAccessControl(file);
                security.AddAccessRule(new FileSystemAccessRule(Sid(WellKnownSidType.LocalServiceSid), FileSystemRights.ReadAndExecute, AccessControlType.Allow));
                File.SetAccessControl(file, security);
            }
            foreach (string directory in Directory.GetDirectories(root)) GrantServiceProgramAccess(directory);
        }

        private static void GrantProgramDirectory(string directory)
        {
            if ((File.GetAttributes(directory) & FileAttributes.ReparsePoint) != 0) throw new IOException("Unexpected program link");
            var security = Directory.GetAccessControl(directory);
            security.AddAccessRule(new FileSystemAccessRule(Sid(WellKnownSidType.LocalServiceSid), FileSystemRights.ReadAndExecute,
                InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit, PropagationFlags.None, AccessControlType.Allow));
            Directory.SetAccessControl(directory, security);
        }

        internal static System.Collections.Generic.Dictionary<string, string> RuntimeInfo(string root)
        {
            var result = new System.Collections.Generic.Dictionary<string, string>();
            foreach (string line in File.ReadAllLines(Path.Combine(RuntimeDirectory(root), "endpoint.properties")))
            {
                int separator = line.IndexOf('=');
                if (separator > 0) result[line.Substring(0, separator)] = line.Substring(separator + 1);
            }
            int port;
            if (!result.ContainsKey("port") || !Int32.TryParse(result["port"], out port) || port < Port || port > LastPort)
                throw new InvalidDataException("Invalid DAC endpoint");
            int pid;
            if (!result.ContainsKey("pid") || !Int32.TryParse(result["pid"], out pid) || pid < 1
                || !result.ContainsKey("publicKey") || !System.Text.RegularExpressions.Regex.IsMatch(result["publicKey"], "\\A[a-f0-9]{64}\\z")
                || !result.ContainsKey("instanceId") || !System.Text.RegularExpressions.Regex.IsMatch(result["instanceId"], "\\A[a-f0-9-]{36}\\z"))
                throw new InvalidDataException("Invalid DAC identity metadata");
            return result;
        }

        internal static int[] CandidatePorts(int previous)
        {
            var ports = new System.Collections.Generic.List<int>();
            if (previous >= Port && previous <= LastPort) ports.Add(previous);
            for (int port = Port; port <= LastPort; port++) if (!ports.Contains(port)) ports.Add(port);
            return ports.ToArray();
        }

        private static SecurityIdentifier Sid(WellKnownSidType type)
        {
            return new SecurityIdentifier(type, null);
        }

        private static void SecureDirectory(string path, bool serviceWrite)
        {
            Directory.CreateDirectory(path);
            var security = new DirectorySecurity();
            security.SetAccessRuleProtection(true, false);
            var inherit = InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit;
            foreach (var sid in new[] { WellKnownSidType.LocalSystemSid, WellKnownSidType.BuiltinAdministratorsSid })
                security.AddAccessRule(new FileSystemAccessRule(Sid(sid), FileSystemRights.FullControl, inherit, PropagationFlags.None, AccessControlType.Allow));
            security.AddAccessRule(new FileSystemAccessRule(Sid(WellKnownSidType.LocalServiceSid),
                serviceWrite ? FileSystemRights.Modify : FileSystemRights.ReadAndExecute, inherit, PropagationFlags.None, AccessControlType.Allow));
            // Users can read only the non-secret configuration file; they cannot list data or read the key.
            Directory.SetAccessControl(path, security);
        }

        private static void SecureFile(string path, bool publicConfig)
        {
            var security = new FileSecurity();
            security.SetAccessRuleProtection(true, false);
            foreach (var sid in new[] { WellKnownSidType.LocalSystemSid, WellKnownSidType.BuiltinAdministratorsSid })
                security.AddAccessRule(new FileSystemAccessRule(Sid(sid), FileSystemRights.FullControl, AccessControlType.Allow));
            security.AddAccessRule(new FileSystemAccessRule(Sid(WellKnownSidType.LocalServiceSid), FileSystemRights.Read, AccessControlType.Allow));
            if (publicConfig) security.AddAccessRule(new FileSystemAccessRule(Sid(WellKnownSidType.BuiltinUsersSid), FileSystemRights.Read, AccessControlType.Allow));
            File.SetAccessControl(path, security);
        }
    }
}
