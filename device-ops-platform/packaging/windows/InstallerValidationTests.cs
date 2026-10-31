using System;
using Microsoft.Deployment.WindowsInstaller;

namespace Npdms.Dac.Desktop
{
    internal static class InstallerValidationTests
    {
        [STAThread]
        private static int Main(string[] args)
        {
            if (args.Length != 1) return 2;
            InstallUIOptions previousUi = Installer.SetInternalUI(InstallUIOptions.Silent);
            try
            {
                Installer.EnableLog(InstallLogModes.Verbose, System.IO.Path.Combine(System.IO.Path.GetDirectoryName(args[0]), "logs/installer-validation-msi.log"));
                Console.WriteLine("Opening MSI validation session");
                // Open a real MSI session, but invoke only the two read-only validation actions.
                // No install sequence, file copying, registry writes or service actions are run.
                using (Session session = Installer.OpenPackage(args[0], false))
                {
                    Require(new Version(session["ProductVersion"]) > new Version("1.1.4"), "fixed installer has a higher product version than previously published 1.1.4 packages");
                    using (var upgrade = session.Database.OpenView("SELECT `VersionMax`, `Attributes` FROM `Upgrade` WHERE `ActionProperty` = 'WIX_UPGRADE_DETECTED'"))
                    {
                        upgrade.Execute();
                        using (var row = upgrade.Fetch())
                        {
                            Require(row != null && new Version(row.GetString(1)) > new Version("1.1.4")
                                && (row.GetInteger(2) & 2) == 0, "native MSI upgrade range includes 1.1.4 and permits program replacement");
                        }
                    }
                    int planSequence = ActionSequence(session, "PlanRemovePreviousInno");
                    int removeSequence = ActionSequence(session, "RemovePreviousInno");
                    Require(planSequence < ActionSequence(session, "InstallInitialize"), "old-installer validation precedes transaction");
                    Require(removeSequence > ActionSequence(session, "InstallInitialize") && removeSequence < ActionSequence(session, "InstallFiles"), "old Inno removal is elevated and precedes new file writes");
                    using (var removal = session.Database.OpenView("SELECT `Type` FROM `CustomAction` WHERE `Action` = 'RemovePreviousInno'"))
                    {
                        removal.Execute();
                        using (var row = removal.Fetch()) Require(row != null && (row.GetInteger(1) & 1024) != 0
                            && (row.GetInteger(1) & 2048) != 0 && (row.GetInteger(1) & 64) == 0, "removal is deferred, privileged and aborts on failure");
                    }
                    session.DoAction("ForceFilesAfterInnoRemoval");
                    Require(session["REINSTALLMODE"] == "amus", "copy all payload files after old Inno removes version-identical runtime files");
                    using (var view = session.Database.OpenView("SELECT `Condition`, `Sequence` FROM `InstallUISequence` WHERE `Action` = 'OpenDesktopStatus'"))
                    {
                        view.Execute();
                        using (var row = view.Fetch())
                        {
                            Require(row != null && row.GetString(1) == "UILevel >= 3 AND NOT REMOVE~=\"ALL\"", "status browser is limited to interactive non-removal UI");
                            using (var execute = session.Database.OpenView("SELECT `Sequence` FROM `InstallUISequence` WHERE `Action` = 'ExecuteAction'"))
                            {
                                execute.Execute();
                                using (var action = execute.Fetch()) Require(row.GetInteger(2) > action.GetInteger(1), "browser follows successful installation");
                            }
                        }
                    }
                    using (var view = session.Database.OpenView("SELECT `Action` FROM `InstallExecuteSequence` WHERE `Action` = 'OpenDesktopStatus'"))
                    {
                        view.Execute();
                        using (var row = view.Fetch()) Require(row == null, "silent execute sequence never opens browser");
                    }
                    session.DoAction("SetDefaultServerUrl");
                    session.DoAction("SetDefaultWebOrigin");
                    Require(session["NPDMS_SERVER_URL"] == session["NPDMS_DEFAULT_SERVER_URL"]
                        && session["NPDMS_WEB_ORIGIN"] == session["NPDMS_DEFAULT_WEB_ORIGIN"], "packaged environment defaults reach installer fields");
                    session.DoAction("ValidateClientSettings");
                    Require(session["NPDMS_CONFIG_VALID"] == "1", "packaged default environment is accepted");
                    Console.WriteLine("Invoking packaged validation action");
                    session["NPDMS_SERVER_URL"] = "10.210.0.11:19191";
                    session["NPDMS_WEB_ORIGIN"] = "";
                    session.DoAction("ValidateClientSettings");
                    Require(session["NPDMS_CONFIG_VALID"] == "" && session["NPDMS_CONFIG_ERROR"].Contains("协议前缀"), "reported input explains missing scheme");

                    session["NPDMS_SERVER_URL"] = "http://10.210.0.11:59191";
                    session["NPDMS_WEB_ORIGIN"] = "http://10.210.0.11:19191";
                    session.DoAction("ValidateClientSettings");
                    Require(session["NPDMS_CONFIG_VALID"] == "1" && session["NPDMS_CONFIG_ERROR"] == "", "approved LAN HTTP addresses install without HTTPS");
                    Require(session["NPDMS_SERVER_URL"] == "http://10.210.0.11:59191" && session["NPDMS_WEB_ORIGIN"] == "http://10.210.0.11:19191", "backend and browser ports remain distinct");

                    session["NPDMS_SERVER_URL"] = " https://npdms.example.com:8443/api/ ";
                    session["NPDMS_WEB_ORIGIN"] = "";
                    session.DoAction("ValidateClientSettings");
                    Require(session["NPDMS_CONFIG_VALID"] == "1" && session["NPDMS_CONFIG_ERROR"] == "", "corrected input can proceed");
                    Require(session["NPDMS_SERVER_URL"] == "https://npdms.example.com:8443/api" && session["NPDMS_WEB_ORIGIN"] == "https://npdms.example.com:8443", "normalized settings reach installer properties");

                    session["NPDMS_SERVER_URL"] = "http://127.0.0.1:59191";
                    session["NPDMS_WEB_ORIGIN"] = "http://localhost:19191";
                    session.DoAction("ValidateClientSettingsForInstall");
                    Require(session["NPDMS_CONFIG_VALID"] == "1", "explicit loopback development addresses remain supported");

                    session["NPDMS_WEB_ORIGIN"] = "http://localhost:19191/page";
                    session.DoAction("ValidateClientSettings");
                    Require(session["NPDMS_CONFIG_VALID"] == "" && session["NPDMS_CONFIG_ERROR"].Contains("不包含页面路径"), "subsequent invalid input clears prior success");

                    session["NPDMS_SERVER_URL"] = "https://user:private-password@npdms.example.com";
                    session["NPDMS_WEB_ORIGIN"] = "";
                    session.DoAction("ValidateClientSettings");
                    Require(session["NPDMS_CONFIG_VALID"] == "" && !session["NPDMS_CONFIG_ERROR"].Contains("private-password"), "error never echoes pasted credentials");

                    bool rejected = false;
                    try { session.DoAction("ValidateClientSettingsForInstall"); }
                    catch (InstallerException) { rejected = true; }
                    Require(rejected, "silent installation cannot bypass configuration validation");
                }
                Console.WriteLine("PASS: real MSI validation actions, LAN HTTP, distinct backend/browser ports, HTTPS normalization, loopback addresses and invalid-input rejection");
                return 0;
            }
            catch (Exception failure)
            {
                Console.Error.WriteLine("Installer check failed: " + failure);
                return 1;
            }
            finally { Installer.SetInternalUI(previousUi); }
        }

        private static int ActionSequence(Session session, string action)
        {
            using (var view = session.Database.OpenView("SELECT `Sequence` FROM `InstallExecuteSequence` WHERE `Action` = ?"))
            using (var parameter = new Record(1))
            {
                parameter[1] = action; view.Execute(parameter);
                using (var row = view.Fetch()) { Require(row != null, "required installer action: " + action); return row.GetInteger(1); }
            }
        }

        private static void Require(bool condition, string name)
        {
            if (!condition) throw new Exception(name);
        }
    }
}
