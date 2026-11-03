using System;
using System.IO;
using System.ServiceProcess;
using WixToolset.Dtf.WindowsInstaller;

namespace Npdms.Dac.Desktop
{
    public static class InstallerActions
    {
        [CustomAction]
        public static ActionResult SetInstallOutcome(Session session)
        {
            bool rebootRequired = session.GetMode(InstallRunMode.RebootAtEnd);
            session["DAC_REBOOT_REQUIRED"] = rebootRequired ? "1" : "";
            session["WIXUI_EXITDIALOGOPTIONALTEXT"] = rebootRequired
                ? "安装已完成，但 Windows 报告仍有文件等待替换。请保存工作后自行重启电脑，无需再次安装。安装器不会自动重启。"
                : "本机 DAC 已安装，并已应用本次确认的环境地址。服务已自动启动，无需重启电脑或重复安装。请刷新业务网页重新检测。";
            return ActionResult.Success;
        }

        [CustomAction]
        public static ActionResult PlanClientUpgrade(Session session)
        {
            try
            {
                var previous = new CustomActionData();
                if (File.Exists(Path.Combine(ClientConfiguration.DataRoot, "client.xml")))
                {
                    var settings = ClientConfiguration.Load(ClientConfiguration.DataRoot);
                    previous["ServerUrl"] = settings.ServerUrl;
                    previous["WebOrigin"] = settings.WebOrigin;
                }
                session["RestoreClientSettings"] = previous.ToString();
                session["DAC_SERVICE_WAS_RUNNING"] = "";
                if (!String.IsNullOrEmpty(session["WIX_UPGRADE_DETECTED"]) && HasOwnedService())
                {
                    using (var service = new ServiceController(ClientConfiguration.ServiceName))
                    {
                        if (service.Status != ServiceControllerStatus.Stopped)
                            session["DAC_SERVICE_WAS_RUNNING"] = "1";
                    }
                }
                return ActionResult.Success;
            }
            catch (Exception failure) { return ReportUpgradeFailure(session, failure); }
        }

        [CustomAction]
        public static ActionResult StopPreviousDac(Session session)
        {
            try
            {
                if (!HasOwnedService()) throw new InvalidOperationException("原 DAC 服务状态已变化。");
                using (var service = new ServiceController(ClientConfiguration.ServiceName))
                {
                    if (service.Status != ServiceControllerStatus.Stopped)
                    {
                        if (service.Status != ServiceControllerStatus.StopPending) service.Stop();
                        service.WaitForStatus(ServiceControllerStatus.Stopped, TimeSpan.FromSeconds(90));
                    }
                }
                session.Log("Previous DAC service stopped before old MSI removal; no computer restart requested.");
                return ActionResult.Success;
            }
            catch (Exception failure) { return ReportUpgradeFailure(session, failure); }
        }

        [CustomAction]
        public static ActionResult RestorePreviousDac(Session session)
        {
            try
            {
                if (!HasOwnedService()) throw new InvalidOperationException("原 DAC 服务未能恢复。");
                using (var service = new ServiceController(ClientConfiguration.ServiceName))
                {
                    if (service.Status == ServiceControllerStatus.StopPending)
                        service.WaitForStatus(ServiceControllerStatus.Stopped, TimeSpan.FromSeconds(90));
                    if (service.Status == ServiceControllerStatus.Stopped) service.Start();
                    service.WaitForStatus(ServiceControllerStatus.Running, TimeSpan.FromSeconds(60));
                }
                return ActionResult.Success;
            }
            catch (Exception failure) { return ReportUpgradeFailure(session, failure); }
        }

        [CustomAction]
        public static ActionResult RestoreClientSettings(Session session)
        {
            try
            {
                var previous = session.CustomActionData;
                if (previous.ContainsKey("ServerUrl"))
                {
                    var settings = new ClientConfiguration(previous["ServerUrl"], previous["WebOrigin"]);
                    settings.Save(ClientConfiguration.DataRoot);
                    BrowserAccessPolicy.Apply(settings);
                }
                else BrowserAccessPolicy.Remove();
                return ActionResult.Success;
            }
            catch (Exception failure) { return ReportUpgradeFailure(session, failure); }
        }

        private static bool HasOwnedService()
        {
            using (var key = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Services\NpdmsDac"))
            {
                if (key == null) return false;
                var executable = Convert.ToString(key.GetValue("ImagePath")).Trim().Trim('"');
                if (!ClientConfiguration.IsOwnedServiceExecutable(executable))
                    throw new InvalidOperationException("DAC 服务不属于标准安装目录，已停止升级。");
                return true;
            }
        }

        private static ActionResult ReportUpgradeFailure(Session session, Exception failure)
        {
            session.Log("DAC upgrade operation failed: " + failure.GetType().Name);
            using (var message = new Record(1))
            {
                message.FormatString = "[1]";
                message[1] = "本机 DAC 服务或配置处理未完成，安装已停止。请查看安装日志；无需重复点击安装或重启电脑。";
                session.Message(InstallMessage.Error, message);
            }
            return ActionResult.Failure;
        }

        [CustomAction]
        public static ActionResult LoadDownloadEnvironment(Session session)
        {
            if (!String.IsNullOrEmpty(session["Installed"]) || session["REMOVE"] == "ALL"
                || session["DOWNLOAD_ENVIRONMENT_LOADED"] == "1") return ActionResult.Success;
            var path = System.IO.Path.Combine(session["OriginalDatabase"].Length == 0 ? "" : System.IO.Path.GetDirectoryName(session["OriginalDatabase"]), "dac-environment.ini");
            if (!System.IO.File.Exists(path)) return ActionResult.Success;
            try
            {
                string server = null, origin = null;
                foreach (var line in System.IO.File.ReadAllLines(path))
                {
                    if (line.StartsWith("ServerUrl=", StringComparison.Ordinal)) server = line.Substring(10);
                    if (line.StartsWith("WebOrigin=", StringComparison.Ordinal)) origin = line.Substring(10);
                }
                var configuration = new ClientConfiguration(server, origin);
                session["SERVER_URL"] = configuration.ServerUrl;
                session["WEB_ORIGIN"] = configuration.WebOrigin;
                session["DOWNLOAD_ENVIRONMENT_LOADED"] = "1";
                return ActionResult.Success;
            }
            catch (Exception)
            {
                session.Log("DAC download environment configuration is invalid.");
                return ActionResult.Failure;
            }
        }

        [CustomAction]
        public static ActionResult PlanRemovePreviousInno(Session session)
        {
            try
            {
                var previous = PreviousInnoInstallation.Read(session["ProductVersion"]);
                if (previous == null) throw new InvalidOperationException("旧版 Inno 安装状态已变化，请重新运行安装器。");
                var data = new CustomActionData();
                data["Executable"] = previous.Executable;
                data["Version"] = previous.Version.ToString();
                data["TargetVersion"] = session["ProductVersion"];
                session["RemovePreviousInno"] = data.ToString();
                return ActionResult.Success;
            }
            catch (Exception failure) { return ReportRemovalFailure(session, failure); }
        }

        [CustomAction]
        public static ActionResult RemovePreviousInno(Session session)
        {
            try
            {
                var data = session.CustomActionData;
                var previous = PreviousInnoInstallation.Read(data["TargetVersion"]);
                if (previous == null || previous.Executable != data["Executable"] || previous.Version.ToString() != data["Version"])
                    throw new InvalidOperationException("旧版安装状态变化。");
                if (!System.IO.File.Exists(previous.Executable)) throw new InvalidOperationException("缺少旧版卸载程序。");
                previous.Remove(new PreviousInnoInstallation.Removal {
                    Run = delegate(System.Diagnostics.ProcessStartInfo start) {
                        using (var process = System.Diagnostics.Process.Start(start)) { process.WaitForExit(); return process.ExitCode; }
                    },
                    RegistrationExists = delegate { using (var root = Microsoft.Win32.RegistryKey.OpenBaseKey(Microsoft.Win32.RegistryHive.LocalMachine, Microsoft.Win32.RegistryView.Registry64))
                        using (var key = root.OpenSubKey(PreviousInnoInstallation.RegistrationKey)) return key != null; },
                    ServiceExists = delegate { using (var key = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Services\NpdmsDac")) return key != null; }
                });
                return ActionResult.Success;
            }
            catch (Exception failure) { return ReportRemovalFailure(session, failure); }
        }

        private static ActionResult ReportRemovalFailure(Session session, Exception failure)
        {
            session.Log("Previous DAC removal failed: " + failure.GetType().Name);
            if (failure is InvalidOperationException) session.Log(failure.Message);
            using (var message = new Record(1))
            {
                message.FormatString = "[1]";
                message[1] = "旧版 DAC 自动卸载未完成（状态变化、版本、权限、卸载失败或需要重启），新版安装已中止。配置、连接、凭据及任务历史保留；请查看安装日志后重新运行安装器。";
                session.Message(InstallMessage.Error, message);
            }
            return ActionResult.Failure;
        }

        // DTF exposes a native MSI entry point before any product files are installed.
        // https://docs.firegiant.com/wix3/tutorial/events-and-actions/how-to-manage/
        [CustomAction]
        public static ActionResult ValidateClientSettings(Session session)
        {
            session["CONFIG_VALID"] = "";
            session["CONFIG_ERROR"] = "";
            try
            {
                var configuration = new ClientConfiguration(session["SERVER_URL"], session["WEB_ORIGIN"]);
                session["SERVER_URL"] = configuration.ServerUrl;
                session["WEB_ORIGIN"] = configuration.WebOrigin;
                session["CONFIG_VALID"] = "1";
            }
            catch (ArgumentException failure)
            {
                // Do not log raw input: a pasted URL may contain credentials.
                session["CONFIG_ERROR"] = failure.Message;
                session.Log("SDP configuration rejected: " + failure.Message);
            }
            return ActionResult.Success;
        }

        [CustomAction]
        public static ActionResult ValidateClientSettingsForInstall(Session session)
        {
            ValidateClientSettings(session);
            if (session["CONFIG_VALID"] == "1") return ActionResult.Success;
            using (var message = new Record(1))
            {
                message.FormatString = "[1]";
                message[1] = session["CONFIG_ERROR"];
                session.Message(InstallMessage.Error, message);
            }
            return ActionResult.Failure;
        }
    }
}
