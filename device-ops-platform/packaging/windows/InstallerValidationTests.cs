using System;
using WixToolset.Dtf.WindowsInstaller;

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
                    using (var directory = session.Database.OpenView("SELECT `DefaultDir` FROM `Directory` WHERE `Directory` = 'CompanyDirectory'"))
                    {
                        directory.Execute();
                        using (var row = directory.Fetch()) Require(row != null && row.GetString(1) == "SDP", "program path uses SDP without moving historical ProgramData");
                    }
                    using (var launcher = session.Database.OpenView("SELECT `FileName` FROM `File` WHERE `File` = 'LauncherExecutable'"))
                    {
                        launcher.Execute();
                        using (var row = launcher.Fetch()) Require(row != null && row.GetString(1).EndsWith("SdpDac.exe", StringComparison.OrdinalIgnoreCase), "protocol launcher process is SdpDac.exe");
                    }
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
                    Require(session["REBOOT"] == "ReallySuppress" && session["MSIRESTARTMANAGERCONTROL"] == "Disable",
                        "service-owned upgrade never asks Restart Manager to kill LocalService child processes or reboot Windows");
                    Require(ActionSequence(session, "ValidateClientSettingsForInstall") < ActionSequence(session, "PlanClientUpgrade")
                        && ActionSequence(session, "PlanClientUpgrade") < ActionSequence(session, "InstallInitialize"), "validate and preserve configuration before transaction");
                    Require(ActionSequence(session, "InstallInitialize") < ActionSequence(session, "RestorePreviousDac")
                        && ActionSequence(session, "RestorePreviousDac") < ActionSequence(session, "StopPreviousDac")
                        && ActionSequence(session, "StopPreviousDac") < ActionSequence(session, "InstallExecute")
                        && ActionSequence(session, "InstallExecute") < ActionSequence(session, "RemoveExistingProducts")
                        && ActionSequence(session, "RemoveExistingProducts") < ActionSequence(session, "InstallFiles"),
                        "flush reversible service shutdown before old MSI validation and before replacing payload files");
                    Require(ActionSequence(session, "InstallFiles") < ActionSequence(session, "RestoreClientSettings")
                        && ActionSequence(session, "RestoreClientSettings") < ActionSequence(session, "InitializeClient")
                        && ActionSequence(session, "InitializeClient") < ActionSequence(session, "StartServices"),
                        "configuration rollback is scheduled before applying settings and before service start");
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
                            Require(row != null && row.GetString(1) == "UILevel >= 3 AND NOT REMOVE~=\"ALL\" AND NOT DAC_REBOOT_REQUIRED", "status browser is limited to completed interactive non-removal UI without pending reboot");
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
                    Require(session["SERVER_URL"] == session["DEFAULT_SERVER_URL"]
                        && session["WEB_ORIGIN"] == session["DEFAULT_WEB_ORIGIN"], "packaged environment defaults reach installer fields");
                    session.DoAction("ValidateClientSettings");
                    Require(session["CONFIG_VALID"] == "1", "packaged default environment is accepted");
                    session.DoAction("PlanClientUpgrade");
                    Require(session["SERVER_URL"] == session["DEFAULT_SERVER_URL"]
                        && session["WEB_ORIGIN"] == session["DEFAULT_WEB_ORIGIN"], "upgrade planning never substitutes existing settings for confirmed inputs");
                    session.SetMode(InstallRunMode.RebootAtEnd, false);
                    session.DoAction("SetInstallOutcome");
                    Require(session["DAC_REBOOT_REQUIRED"] == "" && session["WIXUI_EXITDIALOGOPTIONALTEXT"].Contains("无需重启电脑"), "successful upgrade has one clear completion message");
                    session.SetMode(InstallRunMode.RebootAtEnd, true);
                    session.DoAction("SetInstallOutcome");
                    Require(session["DAC_REBOOT_REQUIRED"] == "1" && session["WIXUI_EXITDIALOGOPTIONALTEXT"].Contains("自行重启电脑"), "a real pending reboot is not hidden or reported as ready");
                    session.SetMode(InstallRunMode.RebootAtEnd, false);
                    Console.WriteLine("Invoking packaged validation action");
                    var environmentFile = System.IO.Path.Combine(System.IO.Path.GetDirectoryName(args[0]), "dac-environment.ini");
                    if (System.IO.File.Exists(environmentFile)) throw new InvalidOperationException("Use an isolated package directory for configuration tests.");
                    try
                    {
                        System.IO.File.WriteAllText(environmentFile, "[Environment]\r\nServerUrl=http://127.0.0.1:58487\r\nWebOrigin=http://127.0.0.1:19089\r\n");
                        session.DoAction("LoadDownloadEnvironment");
                        Require(session["SERVER_URL"] == "http://127.0.0.1:58487" && session["WEB_ORIGIN"] == "http://127.0.0.1:19089", "download environment file prefills distinct server and browser addresses");
                        session["SERVER_URL"] = "https://sdp.example.com";
                        session.DoAction("LoadDownloadEnvironment");
                        Require(session["SERVER_URL"] == "https://sdp.example.com", "execute sequence preserves user edits after initial fill");
                        session["DOWNLOAD_ENVIRONMENT_LOADED"] = "";
                        System.IO.File.WriteAllText(environmentFile, "ServerUrl=https://user:secret@sdp.example.com\r\nWebOrigin=https://sdp.example.com\r\n");
                        bool invalidEnvironmentRejected = false;
                        try { session.DoAction("LoadDownloadEnvironment"); }
                        catch (InstallerException) { invalidEnvironmentRejected = true; }
                        Require(invalidEnvironmentRejected && session["DOWNLOAD_ENVIRONMENT_LOADED"] != "1"
                            && session["SERVER_URL"] == "https://sdp.example.com", "invalid download configuration aborts without partially applying settings");
                        session["Installed"] = "1";
                        session.DoAction("LoadDownloadEnvironment");
                        Require(session["SERVER_URL"] == "https://sdp.example.com", "maintenance does not replace settings from a neighboring download file");
                        session["Installed"] = "";
                    }
                    finally { System.IO.File.Delete(environmentFile); }
                    session["SERVER_URL"] = "10.210.0.11:19191";
                    session["WEB_ORIGIN"] = "";
                    session.DoAction("ValidateClientSettings");
                    Require(session["CONFIG_VALID"] == "" && session["CONFIG_ERROR"].Contains("协议前缀"), "reported input explains missing scheme");

                    session["SERVER_URL"] = "http://10.210.0.11:59191";
                    session["WEB_ORIGIN"] = "http://10.210.0.11:19191";
                    session.DoAction("ValidateClientSettings");
                    Require(session["CONFIG_VALID"] == "1" && session["CONFIG_ERROR"] == "", "approved LAN HTTP addresses install without HTTPS");
                    Require(session["SERVER_URL"] == "http://10.210.0.11:59191" && session["WEB_ORIGIN"] == "http://10.210.0.11:19191", "backend and browser ports remain distinct");

                    session["SERVER_URL"] = " https://npdms.example.com:8443/api/ ";
                    session["WEB_ORIGIN"] = "";
                    session.DoAction("ValidateClientSettings");
                    Require(session["CONFIG_VALID"] == "1" && session["CONFIG_ERROR"] == "", "corrected input can proceed");
                    Require(session["SERVER_URL"] == "https://npdms.example.com:8443/api" && session["WEB_ORIGIN"] == "https://npdms.example.com:8443", "normalized settings reach installer properties");

                    session["SERVER_URL"] = "http://127.0.0.1:59191";
                    session["WEB_ORIGIN"] = "http://localhost:19191";
                    session.DoAction("ValidateClientSettingsForInstall");
                    Require(session["CONFIG_VALID"] == "1", "explicit loopback development addresses remain supported");

                    session["WEB_ORIGIN"] = "http://localhost:19191/page";
                    session.DoAction("ValidateClientSettings");
                    Require(session["CONFIG_VALID"] == "" && session["CONFIG_ERROR"].Contains("不包含页面路径"), "subsequent invalid input clears prior success");

                    session["SERVER_URL"] = "https://user:private-password@npdms.example.com";
                    session["WEB_ORIGIN"] = "";
                    session.DoAction("ValidateClientSettings");
                    Require(session["CONFIG_VALID"] == "" && !session["CONFIG_ERROR"].Contains("private-password"), "error never echoes pasted credentials");

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
