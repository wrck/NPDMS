using System;
using System.IO;
using System.Linq;

namespace Npdms.Dac.Desktop
{
    internal static class ClientConfigurationTests
    {
        private static int Main(string[] args)
        {
            try { return Check(args); }
            catch (Exception failure) { Console.Error.WriteLine("Native check failed: " + failure.GetType().Name + ": " + failure.Message); return 1; }
        }

        private static int Check(string[] args)
        {
            if (args.Length != 1) return 2;
            var first = new ClientConfiguration("https://npdms.example.com:8443/", "https://web.example.com:9443");
            Require(first.ServerUrl == "https://npdms.example.com:8443", "port and normalization");
            Require(new ClientConfiguration("http://127.0.0.1:59191", "http://localhost:3000").WebOrigin == "http://localhost:3000", "loopback development");
            var intranet = new ClientConfiguration("http://10.210.0.11:59191", "http://10.210.0.11:19191");
            Require(intranet.ServerUrl == "http://10.210.0.11:59191" && intranet.WebOrigin == "http://10.210.0.11:19191", "approved LAN HTTP environment");
            Require(new ClientConfiguration("https://npdms.example.com/api", null).WebOrigin == "https://npdms.example.com", "default browser origin");
            foreach (string invalid in new[] { "", "10.210.0.11:19191", "ftp://npdms.example.com", "http://*.example.com", "https://user:password@npdms.example.com", "file:///C:/temp", "https://npdms.example.com/?token=secret", "https://npdms.example.com/#fragment", "https://npdms.example.com/\r\n--run", "https://npdms.example.com:99999" })
            {
                bool rejected = false;
                try { new ClientConfiguration(invalid, null); } catch (ArgumentException) { rejected = true; }
                Require(rejected, "reject unsafe address");
            }
            Require(ClientConfiguration.IsStartUri("npdms-dac://start/"), "fixed startup action");
            foreach (string invalid in new[] { "npdms-dac://start/?url=https://evil.example", "npdms-dac://stop/", "npdms-dac://start/--run", "npdms-dac://start/#secret", "cmd.exe" })
                Require(!ClientConfiguration.IsStartUri(invalid), "reject arbitrary protocol payload");
            var second = new ClientConfiguration("https://another.example.com:8443", null);
            Require(first.EnvironmentDirectory(args[0]) != second.EnvironmentDirectory(args[0]), "isolate server environments");
            CheckBrowserPolicyOwnership(intranet);
            byte[] probe = new byte[32];
            using (var random = System.Security.Cryptography.RandomNumberGenerator.Create()) random.GetBytes(probe);
            byte[] protectedProbe = System.Security.Cryptography.ProtectedData.Protect(probe, null, System.Security.Cryptography.DataProtectionScope.LocalMachine);
            Require(probe.SequenceEqual(System.Security.Cryptography.ProtectedData.Unprotect(protectedProbe, null, System.Security.Cryptography.DataProtectionScope.LocalMachine)), "DPAPI machine protection round trip");
            Array.Clear(probe, 0, probe.Length);
            var principal = new System.Security.Principal.WindowsPrincipal(System.Security.Principal.WindowsIdentity.GetCurrent());
            if (!principal.IsInRole(System.Security.Principal.WindowsBuiltInRole.Administrator))
            {
                Console.WriteLine("PASS: URL validation, protocol restrictions, environment isolation and DPAPI round trip");
                Console.WriteLine("NOT_RUN: privileged configuration installation, DPAPI persistence and credential ACL (administrator token required)");
                return 0;
            }
            first.Save(args[0]);
            byte[] key = ClientConfiguration.ReadMasterKey(args[0]);
            byte[] encrypted = File.ReadAllBytes(Path.Combine(args[0], "credentials.key.dpapi"));
            Require(key.Length == 32 && !key.SequenceEqual(encrypted), "DPAPI encrypted master key");
            first.Save(args[0]);
            Require(key.SequenceEqual(ClientConfiguration.ReadMasterKey(args[0])), "configuration update preserves credential key");
            Require(ClientConfiguration.Load(args[0]).WebOrigin == first.WebOrigin, "configuration round trip");
            string acl = File.GetAccessControl(Path.Combine(args[0], "credentials.key.dpapi")).GetSecurityDescriptorSddlForm(System.Security.AccessControl.AccessControlSections.Access);
            Require(!acl.Contains(";;;BU)") && acl.Contains(";;;LS)"), "credential key is restricted to service and administrators");
            Array.Clear(key, 0, key.Length);
            Console.WriteLine("PASS: URL validation, protocol restrictions, environment isolation, DPAPI persistence and credential ACL");
            return 0;
        }

        private static void Require(bool condition, string name)
        {
            if (!condition) throw new Exception(name);
        }

        private static void CheckBrowserPolicyOwnership(ClientConfiguration configuration)
        {
            string testPath = @"Software\NPDMS\DacPolicyTests\" + Guid.NewGuid().ToString("N");
            try
            {
                using (var root = Microsoft.Win32.Registry.CurrentUser.CreateSubKey(testPath))
                {
                    using (var chrome = root.CreateSubKey(BrowserAccessPolicy.ChromePath))
                        chrome.SetValue("1", "https://company-admin.example");
                    using (var edge = root.CreateSubKey(BrowserAccessPolicy.EdgePath))
                        edge.SetValue("1", configuration.WebOrigin);
                    BrowserAccessPolicy.Apply(root, configuration);
                    BrowserAccessPolicy.Apply(root, configuration);
                    using (var chrome = root.OpenSubKey(BrowserAccessPolicy.ChromePath, true))
                    {
                        Require(chrome.ValueCount == 2 && (string)chrome.GetValue("2") == configuration.WebOrigin, "exact HTTP origin and repeated installation");
                        Require((string)chrome.GetValue("1") == "https://company-admin.example", "preserve existing policy entries");
                        chrome.SetValue("2", "https://externally-edited.example");
                    }
                    BrowserAccessPolicy.Apply(root, null);
                    using (var chrome = root.OpenSubKey(BrowserAccessPolicy.ChromePath))
                        Require((string)chrome.GetValue("2") == "https://externally-edited.example", "do not delete externally changed entries");
                    using (var edge = root.OpenSubKey(BrowserAccessPolicy.EdgePath))
                        Require(edge.ValueCount == 1 && (string)edge.GetValue("1") == configuration.WebOrigin, "pre-existing matching origin is not owned by DAC");
                    BrowserAccessPolicy.Apply(root, configuration);
                    var changed = new ClientConfiguration(configuration.ServerUrl, "http://10.210.0.11:29191");
                    BrowserAccessPolicy.Apply(root, changed);
                    using (var chrome = root.OpenSubKey(BrowserAccessPolicy.ChromePath))
                        Require(chrome.ValueCount == 3 && (string)chrome.GetValue("3") == changed.WebOrigin, "environment change replaces only DAC owned origin");
                    BrowserAccessPolicy.Apply(root, new ClientConfiguration("https://npdms.example", "https://web.example"));
                    using (var chrome = root.OpenSubKey(BrowserAccessPolicy.ChromePath))
                        Require(chrome.ValueCount == 2, "leaving HTTP removes only DAC owned exemptions");
                    using (var edge = root.OpenSubKey(BrowserAccessPolicy.EdgePath))
                        Require(edge.ValueCount == 1, "cleanup retains unrelated or pre-existing authorization");
                }
                Console.WriteLine("PASS: isolated registry tests for exact browser origin, idempotency, environment changes and ownership-safe cleanup");
            }
            finally { Microsoft.Win32.Registry.CurrentUser.DeleteSubKeyTree(testPath, false); }
        }
    }
}
