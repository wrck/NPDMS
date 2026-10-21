using System;
using Microsoft.Deployment.WindowsInstaller;

namespace Npdms.Dac.Desktop
{
    public static class InstallerActions
    {
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
