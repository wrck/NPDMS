using System;
using System.Diagnostics;
using System.IO;
using System.Collections;
using System.Collections.Generic;

namespace Npdms.Dac.Desktop
{
    internal static class RuntimeHost
    {
        private static int Main()
        {
            byte[] key = null;
            try
            {
                // WinSW signals the console shared with Java. Keep this host alive until Java finishes its shutdown hooks.
                Console.CancelKeyPress += delegate(object sender, ConsoleCancelEventArgs e) { e.Cancel = true; };
                var settings = ClientConfiguration.Load(ClientConfiguration.DataRoot);
                key = ClientConfiguration.ReadMasterKey(ClientConfiguration.DataRoot);
                string install = AppDomain.CurrentDomain.BaseDirectory;
                var launch = new ProcessStartInfo(Path.Combine(install, "runtime", "bin", "java.exe"))
                {
                    Arguments = "-Xms128m -Xmx768m -Dfile.encoding=UTF-8 -Djavax.net.ssl.trustStoreType=Windows-ROOT-LOCALMACHINE -Djavax.net.ssl.trustStore=NONE -jar \""
                        + Path.Combine(install, "app", "device-ops-server.jar")
                        + "\" --spring.profiles.active=desktop --spring.config.location=classpath:/application.yml,classpath:/application-desktop.yml"
                        + " --device-ops.desktop.parser-release-directory=\"" + Path.Combine(install, "parser-release") + "\"",
                    WorkingDirectory = settings.EnvironmentDirectory(ClientConfiguration.DataRoot),
                    UseShellExecute = false,
                    CreateNoWindow = false,
                    WindowStyle = ProcessWindowStyle.Hidden
                };
                // The installed service owns its configuration and local H2 data; unrelated developer environment variables must not redirect it.
                var inherited = new List<string>();
                foreach (DictionaryEntry entry in launch.EnvironmentVariables) inherited.Add((string)entry.Key);
                foreach (string name in inherited)
                {
                    string upper = name.ToUpperInvariant();
                    if (upper.StartsWith("SPRING_") || upper.StartsWith("DEVICE_OPS_") || upper.StartsWith("NPDMS_")
                        || upper.StartsWith("OIDC_") || upper.StartsWith("MANAGEMENT_") || upper.StartsWith("LOGGING_")
                        || upper == "SERVER_PORT" || upper == "SERVER_ADDRESS" || upper == "JAVA_TOOL_OPTIONS"
                        || upper == "JDK_JAVA_OPTIONS" || upper == "_JAVA_OPTIONS") launch.EnvironmentVariables.Remove(name);
                }
                launch.EnvironmentVariables["NPDMS_SERVER_URL"] = settings.ServerUrl;
                launch.EnvironmentVariables["NPDMS_WEB_ORIGIN"] = settings.WebOrigin;
                launch.EnvironmentVariables["DEVICE_OPS_CREDENTIAL_MASTER_KEY"] = Convert.ToBase64String(key);
                using (var process = Process.Start(launch))
                {
                    launch.EnvironmentVariables.Remove("DEVICE_OPS_CREDENTIAL_MASTER_KEY");
                    Array.Clear(key, 0, key.Length);
                    process.WaitForExit();
                    return process.ExitCode;
                }
            }
            catch (Exception failure)
            {
                // Never print configuration, command arguments, environment variables or decrypted material.
                Console.Error.WriteLine("DAC runtime could not start: " + failure.GetType().Name);
                return 1;
            }
            finally { if (key != null) Array.Clear(key, 0, key.Length); }
        }
    }
}
