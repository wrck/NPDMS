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
            CheckAutomaticRemoval(args[0]);
            CheckServiceProgramPermissions(args[0]);
            Require(ClientConfiguration.IsOwnedServiceExecutable(Path.Combine(ClientConfiguration.InstallDirectory, "SdpDacService.exe"))
                && ClientConfiguration.IsOwnedServiceExecutable(Path.Combine(ClientConfiguration.LegacyInstallDirectory, "NpdmsDacService.exe")), "upgrade recognizes only the SDP installation and its legacy NPDMS predecessor");
            Require(!ClientConfiguration.IsOwnedServiceExecutable(Path.Combine(args[0], "SdpDacService.exe"))
                && !ClientConfiguration.IsOwnedServiceExecutable(Path.Combine(ClientConfiguration.InstallDirectory, "NpdmsDacService.exe")), "renaming does not grant ownership of arbitrary service paths");
            Require(ClientConfiguration.CandidatePorts(8289).SequenceEqual(new[] { 8289, 8280, 8281, 8282, 8283, 8284, 8285, 8286, 8287, 8288 }), "reuse previous port and exhaust every candidate once");
            Require(ClientConfiguration.CandidatePorts(48182).SequenceEqual(Enumerable.Range(8280, 10)), "legacy port does not escape candidate range");
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
            Require(ClientConfiguration.IsStartUri("sdp-dac://start/"), "fixed startup action");
            string connect = "sdp-dac://connect/" + new string('a', 32) + "?challenge=" + new string('b', 64) + "&tenant=1";
            Require(ClientConfiguration.ParseConnectUri(connect).SequenceEqual(new[] { new string('a', 32), new string('b', 64), "1" }), "one-click challenge action");
            foreach (string invalid in new[] { connect + "&url=http://evil", connect + "#fragment", connect.Replace("tenant=1", "tenant=0"),
                connect.Replace("tenant=1", "tenant=-1"), connect.Replace("?challenge=", "?challenge=%22"), connect.Replace("connect/", "connect/../"),
                connect.Replace("&tenant=1", "&tenant=1&tenant=2"), "cmd.exe", "sdp-dac://connect/" })
            {
                bool rejected = false;
                try { ClientConfiguration.ParseConnectUri(invalid); } catch (ArgumentException) { rejected = true; }
                Require(rejected, "reject unsafe connect action");
            }
            foreach (string invalid in new[] { "sdp-dac://start/?url=https://evil.example", "sdp-dac://stop/", "sdp-dac://start/--run", "sdp-dac://start/#secret", "cmd.exe" })
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
            string historical = Path.Combine(first.EnvironmentDirectory(args[0]), "history-preservation-probe.bin");
            File.WriteAllBytes(historical, new byte[] { 3, 1, 4, 1, 5 });
            second.Save(args[0]);
            Require(ClientConfiguration.Load(args[0]).ServerUrl == second.ServerUrl
                && ClientConfiguration.Load(args[0]).WebOrigin == second.WebOrigin, "upgrade applies the newly confirmed environment");
            Require(File.ReadAllBytes(historical).SequenceEqual(new byte[] { 3, 1, 4, 1, 5 })
                && File.ReadAllBytes(Path.Combine(args[0], "credentials.key.dpapi")).SequenceEqual(encrypted), "environment change preserves old history and encrypted master key");
            first.Save(args[0]);
            Require(ClientConfiguration.Load(args[0]).ServerUrl == first.ServerUrl
                && File.ReadAllBytes(historical).SequenceEqual(new byte[] { 3, 1, 4, 1, 5 }), "rollback restores the prior environment without rewriting history");
            string acl = File.GetAccessControl(Path.Combine(args[0], "credentials.key.dpapi")).GetSecurityDescriptorSddlForm(System.Security.AccessControl.AccessControlSections.Access);
            Require(!acl.Contains(";;;BU)") && acl.Contains(";;;LS)"), "credential key is restricted to service and administrators");
            Array.Clear(key, 0, key.Length);
            Console.WriteLine("PASS: URL validation, protocol restrictions, environment isolation, DPAPI persistence and credential ACL");
            return 0;
        }

        private static void CheckServiceProgramPermissions(string root)
        {
            string program = Path.Combine(root, "program-acl-probe");
            Directory.CreateDirectory(Path.Combine(program, "runtime", "bin"));
            string host = Path.Combine(program, "SdpDacHost.exe");
            string java = Path.Combine(program, "runtime", "bin", "java.exe");
            File.WriteAllText(host, "readonly-program-probe"); File.WriteAllText(java, "readonly-runtime-probe");
            string untouched = Path.Combine(root, "preserved-data-probe");
            File.WriteAllText(untouched, "preserve-existing-data");
            string dataAcl = File.GetAccessControl(untouched).GetSecurityDescriptorSddlForm(System.Security.AccessControl.AccessControlSections.Access);
            ClientConfiguration.GrantServiceProgramAccess(program);
            foreach (string file in new[] { host, java })
            {
                bool granted = false;
                foreach (System.Security.AccessControl.FileSystemAccessRule rule in File.GetAccessControl(file).GetAccessRules(true, true, typeof(System.Security.Principal.SecurityIdentifier)))
                {
                    if (rule.IdentityReference.Value != "S-1-5-19" || rule.AccessControlType != System.Security.AccessControl.AccessControlType.Allow) continue;
                    Require((rule.FileSystemRights & System.Security.AccessControl.FileSystemRights.WriteData) == 0, "LocalService cannot modify program files");
                    granted |= (rule.FileSystemRights & System.Security.AccessControl.FileSystemRights.ReadAndExecute) == System.Security.AccessControl.FileSystemRights.ReadAndExecute;
                }
                Require(granted, "LocalService has explicit read/execute for host and bundled Java");
            }
            Require(File.ReadAllText(untouched) == "preserve-existing-data" && File.GetAccessControl(untouched).GetSecurityDescriptorSddlForm(System.Security.AccessControl.AccessControlSections.Access) == dataAcl,
                "program permission change leaves sibling data and ACL unchanged");
            Console.WriteLine("PASS: real isolated program ACL grants LocalService read/execute without write or data permission changes");
        }

        private static void CheckAutomaticRemoval(string root)
        {
            string directory = Path.GetFullPath(Path.Combine(root, "old-install"));
            string executable = Path.Combine(directory, "unins000.exe");
            Func<PreviousInnoInstallation.Registration> registration = delegate {
                return new PreviousInnoInstallation.Registration { InstallDirectory = directory, ExpectedDirectories = new[] { directory },
                    UninstallCommand = "\"" + executable + "\"", DisplayVersion = "1.0.6", TargetVersion = "1.1.0" };
            };
            var previous = PreviousInnoInstallation.Parse(registration());
            Require(previous.Executable == executable, "registered old uninstaller is parsed without shell arguments");
            for (int scenario = 0; scenario < 5; scenario++)
            {
                var invalid = registration();
                if (scenario == 0) invalid.DisplayVersion = "9.0.0";
                if (scenario == 1) invalid.DisplayVersion = "invalid";
                if (scenario == 2) invalid.UninstallCommand += " /arbitrary-command";
                if (scenario == 3) invalid.UninstallCommand = "\"" + Path.Combine(root, "unins000.exe") + "\"";
                if (scenario == 4) invalid.InstallDirectory = root;
                bool rejected = false;
                try { PreviousInnoInstallation.Parse(invalid); } catch (Exception) { rejected = true; }
                Require(rejected, "reject downgrade, unknown version and unrelated uninstall entry");
            }
            foreach (int result in new[] { 0, 1, 1603, 3010 })
            {
                int calls = 0;
                bool accepted = true;
                try { previous.Remove(new PreviousInnoInstallation.Removal {
                    Run = delegate(System.Diagnostics.ProcessStartInfo start) {
                        calls++; Require(start.FileName == executable && !start.UseShellExecute
                            && start.Arguments == "/VERYSILENT /SUPPRESSMSGBOXES /NORESTART", "fixed non-shell silent uninstall without reboot");
                        return result;
                    }, RegistrationExists = delegate { return false; }, ServiceExists = delegate { return false; }
                }); } catch (InvalidOperationException) { accepted = false; }
                Require(accepted == (result == 0) && calls == 1, "failed or reboot-required removal aborts without retry");
            }
            for (int residue = 0; residue < 2; residue++)
            {
                bool rejected = false;
                try { previous.Remove(new PreviousInnoInstallation.Removal {
                    Run = delegate { return 0; }, RegistrationExists = delegate { return residue == 0; }, ServiceExists = delegate { return residue == 1; }
                }); } catch (InvalidOperationException) { rejected = true; }
                Require(rejected, "successful process exit is insufficient while old registration or service remains");
            }
            Console.WriteLine("PASS: automatic uninstall version/path guards, exit failures, reboot and residual ownership protection; no real installer invoked");
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
