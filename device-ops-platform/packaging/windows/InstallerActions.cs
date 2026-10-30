using System;
using Microsoft.Deployment.WindowsInstaller;

namespace Npdms.Dac.Desktop
{
    public static class InstallerActions
    {
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
            session["NPDMS_CONFIG_VALID"] = "";
            session["NPDMS_CONFIG_ERROR"] = "";
            try
            {
                var configuration = new ClientConfiguration(session["NPDMS_SERVER_URL"], session["NPDMS_WEB_ORIGIN"]);
                session["NPDMS_SERVER_URL"] = configuration.ServerUrl;
                session["NPDMS_WEB_ORIGIN"] = configuration.WebOrigin;
                session["NPDMS_CONFIG_VALID"] = "1";
            }
            catch (ArgumentException failure)
            {
                // Do not log raw input: a pasted URL may contain credentials.
                session["NPDMS_CONFIG_ERROR"] = failure.Message;
                session.Log("NPDMS configuration rejected: " + failure.Message);
            }
            return ActionResult.Success;
        }

        [CustomAction]
        public static ActionResult ValidateClientSettingsForInstall(Session session)
        {
            ValidateClientSettings(session);
            if (session["NPDMS_CONFIG_VALID"] == "1") return ActionResult.Success;
            using (var message = new Record(1))
            {
                message.FormatString = "[1]";
                message[1] = session["NPDMS_CONFIG_ERROR"];
                session.Message(InstallMessage.Error, message);
            }
            return ActionResult.Failure;
        }
    }
}
