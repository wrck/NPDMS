using System;
using System.Diagnostics;
using System.Drawing;
using System.Security.Principal;
using System.ServiceProcess;
using System.Windows.Forms;

namespace Npdms.Dac.Desktop
{
    internal static class Launcher
    {
        [STAThread]
        private static int Main(string[] args)
        {
            try
            {
                if (args.Length == 1 && args[0] == "--validate-settings") return 2;
                if (args.Length == 3 && args[0] == "--validate-settings")
                {
                    new ClientConfiguration(args[1], args[2]);
                    return 0;
                }
                if (args.Length == 1 && (args[0] == "--service-stop" || args[0] == "--service-install" || args[0] == "--service-uninstall"))
                {
                    if (!new WindowsPrincipal(WindowsIdentity.GetCurrent()).IsInRole(WindowsBuiltInRole.Administrator)) return 1;
                    bool exists = false;
                    foreach (var installed in ServiceController.GetServices())
                        using (installed) { if (installed.ServiceName == ClientConfiguration.ServiceName) exists = true; }
                    if (!exists && args[0] != "--service-install") return 0;
                    if (args[0] == "--service-stop")
                    {
                        using (var service = new ServiceController(ClientConfiguration.ServiceName))
                        {
                            if (service.Status != ServiceControllerStatus.Stopped)
                            {
                                if (service.Status != ServiceControllerStatus.StopPending) service.Stop();
                                service.WaitForStatus(ServiceControllerStatus.Stopped, TimeSpan.FromSeconds(60));
                            }
                        }
                        return 0;
                    }
                    var command = new ProcessStartInfo(System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "NpdmsDacService.exe"),
                        args[0] == "--service-install" ? "install" : "uninstall") { UseShellExecute = false, CreateNoWindow = true };
                    using (var process = Process.Start(command)) { process.WaitForExit(); return process.ExitCode; }
                }
                if (args.Length == 1 && args[0] == "--service-start") { StartService(); return 0; }
                if (args.Length == 1 && args[0] == "--open-status")
                {
                    string address = VerifiedStatusAddress(AppDomain.CurrentDomain.BaseDirectory);
                    Process.Start(new ProcessStartInfo(address) { UseShellExecute = true });
                    return 0;
                }
                if (args.Length == 2 && args[0] == "--uri")
                {
                    if (!ClientConfiguration.IsStartUri(args[1]))
                    {
                        var connection = ClientConfiguration.ParseConnectUri(args[1]);
                        var settings = ClientConfiguration.Load(ClientConfiguration.DataRoot);
                        StartService();
                        string proof = VerifiedStatusPayload(AppDomain.CurrentDomain.BaseDirectory, connection[1]);
                        var serializer = new System.Web.Script.Serialization.JavaScriptSerializer();
                        var status = serializer.Deserialize<System.Collections.Generic.Dictionary<string, object>>(proof);
                        if ((string)status["serverUrl"] != settings.ServerUrl || (string)status["webOrigin"] != settings.WebOrigin)
                            throw new ArgumentException("本机 DAC 环境与设置不一致，请检查设置后重新连接。");
                        // The destination comes exclusively from the protected installation configuration.
                        var request = System.Net.WebRequest.CreateHttp(settings.ServerUrl
                            + "/api/v1/pms/integration/device-ops/desktop/connections/" + connection[0] + "/proof");
                        request.Method = "POST";
                        request.AllowAutoRedirect = false;
                        request.Timeout = 10000;
                        request.ReadWriteTimeout = 10000;
                        request.ContentType = "application/json";
                        request.Headers["tenant-id"] = connection[2];
                        byte[] body = System.Text.Encoding.UTF8.GetBytes(serializer.Serialize(new { nonce = connection[1], status = status }));
                        request.ContentLength = body.Length;
                        using (var stream = request.GetRequestStream()) stream.Write(body, 0, body.Length);
                        using (var response = (System.Net.HttpWebResponse)request.GetResponse())
                        {
                            if (response.StatusCode != System.Net.HttpStatusCode.OK)
                                throw new ArgumentException("本机连接确认未完成，请回到 SDP 网页重新连接。");
                            using (var reader = new System.IO.StreamReader(response.GetResponseStream()))
                            {
                                var result = serializer.Deserialize<System.Collections.Generic.Dictionary<string, object>>(reader.ReadToEnd());
                                if (!result.ContainsKey("code") || Convert.ToInt32(result["code"]) != 0 || !result.ContainsKey("data") || !Object.Equals(result["data"], true))
                                    throw new ArgumentException("本机连接确认未完成，请回到 SDP 网页重新连接。");
                            }
                        }
                        return 0;
                    }
                    StartService();
                    return 0;
                }
                if (args.Length == 3 && args[0] == "--initialize")
                {
                    ClientConfiguration.GrantServiceProgramAccess(AppDomain.CurrentDomain.BaseDirectory);
                    var configuration = System.IO.File.Exists(System.IO.Path.Combine(ClientConfiguration.DataRoot, "client.xml"))
                        ? ClientConfiguration.Load(ClientConfiguration.DataRoot) : new ClientConfiguration(args[1], args[2]);
                    configuration.Save(ClientConfiguration.DataRoot);
                    BrowserAccessPolicy.Apply(configuration);
                    return 0;
                }
                if (args.Length == 1 && args[0] == "--remove-browser-policy")
                {
                    BrowserAccessPolicy.Remove();
                    return 0;
                }
                if (args.Length == 3 && args[0] == "--apply-settings")
                {
                    SettingsForm.ApplyConfiguration(new ClientConfiguration(args[1], args[2]));
                    return 0;
                }
                if (args.Length != 0 && !(args.Length == 1 && args[0] == "--configure")) return 2;
                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                Application.Run(new SettingsForm());
                return 0;
            }
            catch (Exception failure)
            {
                if (args.Length > 0 && (args[0] == "--initialize" || args[0] == "--remove-browser-policy" || args[0] == "--validate-settings" || args[0].StartsWith("--service-", StringComparison.Ordinal)))
                {
                    try {
                        System.ComponentModel.Win32Exception native = null;
                        for (Exception cause = failure; cause != null; cause = cause.InnerException)
                            if (cause is System.ComponentModel.Win32Exception) { native = (System.ComponentModel.Win32Exception)cause; break; }
                        System.IO.File.AppendAllText(System.IO.Path.Combine(ClientConfiguration.DataRoot, "logs", "installer-native.log"),
                            DateTime.UtcNow.ToString("o") + " " + args[0] + " " + failure.GetType().Name
                            + (native == null ? "" : " Win32=" + native.NativeErrorCode) + Environment.NewLine);
                    } catch { }
                    return 1;
                }
                MessageBox.Show(failure is ArgumentException ? failure.Message : "DAC 操作失败，请检查服务安装状态，或联系管理员。", "SDP DAC", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return 1;
            }
        }

        // Signed nonce and instance binding prove freshness without a privileged service-process handle.
        internal static string VerifiedStatusAddress(string install)
        {
            byte[] challenge = new byte[32];
            using (var random = System.Security.Cryptography.RandomNumberGenerator.Create()) random.GetBytes(challenge);
            string nonce = BitConverter.ToString(challenge).Replace("-", "").ToLowerInvariant();
            var status = new System.Web.Script.Serialization.JavaScriptSerializer().Deserialize<System.Collections.Generic.Dictionary<string, object>>(VerifiedStatusPayload(install, nonce));
            return "http://127.0.0.1:" + status["port"] + "/desktop-status";
        }

        internal static string VerifiedStatusPayload(string install, string nonce)
        {
            if (!System.Text.RegularExpressions.Regex.IsMatch(nonce ?? "", "\\A[a-f0-9]{64}\\z")) throw new ArgumentException("本机连接挑战无效。");
            // Run in the installing user's UI session, never in the service or elevated execute sequence.
            var deadline = DateTime.UtcNow.AddSeconds(60);
            while (true)
            {
                try
                {
                    var endpoint = ClientConfiguration.RuntimeInfo(ClientConfiguration.DataRoot);
                    var request = System.Net.WebRequest.CreateHttp("http://127.0.0.1:" + endpoint["port"] + "/api/v1/desktop/status?nonce=" + nonce);
                    request.Proxy = null;
                    request.Timeout = 1500;
                    using (var response = request.GetResponse())
                    using (var reader = new System.IO.StreamReader(response.GetResponseStream()))
                    {
                        string payload = reader.ReadToEnd();
                        var status = new System.Web.Script.Serialization.JavaScriptSerializer().Deserialize<System.Collections.Generic.Dictionary<string, object>>(payload);
                        if ((string)status["product"] != "npdms-dac" || (string)status["publicKey"] != endpoint["publicKey"]
                            || (string)status["instanceId"] != endpoint["instanceId"] || (string)status["status"] != "READY")
                            throw new System.IO.IOException("DAC runtime metadata differs");
                        var verify = new ProcessStartInfo(System.IO.Path.Combine(install, "runtime", "bin", "java.exe"),
                            "-Dloader.main=com.dp.plat.deviceops.server.desktop.DesktopDiscoveryVerifier -cp \""
                            + System.IO.Path.Combine(install, "app", "device-ops-server.jar")
                            + "\" org.springframework.boot.loader.launch.PropertiesLauncher " + endpoint["publicKey"] + " " + nonce + " " + endpoint["port"])
                            { UseShellExecute = false, CreateNoWindow = true, RedirectStandardInput = true };
                        var inherited = new System.Collections.Generic.List<string>();
                        foreach (System.Collections.DictionaryEntry entry in verify.EnvironmentVariables) inherited.Add((string)entry.Key);
                        foreach (string name in inherited)
                        {
                            string upper = name.ToUpperInvariant();
                            if (upper.StartsWith("LOADER_", StringComparison.Ordinal) || upper == "JAVA_TOOL_OPTIONS"
                                || upper == "JDK_JAVA_OPTIONS" || upper == "_JAVA_OPTIONS" || upper == "CLASSPATH") verify.EnvironmentVariables.Remove(name);
                        }
                        using (var verifier = Process.Start(verify))
                        {
                            verifier.StandardInput.Write(payload); verifier.StandardInput.Close();
                            if (!verifier.WaitForExit(10000)) { verifier.Kill(); throw new System.IO.IOException("DAC identity verification timed out"); }
                            if (verifier.ExitCode != 0) throw new System.IO.IOException("DAC identity verification failed");
                        }
                        return payload;
                    }
                }
                catch (Exception failure)
                {
                    if (!(failure is System.Net.WebException || failure is System.IO.IOException || failure is ArgumentException)) throw;
                    if (DateTime.UtcNow >= deadline)
                        throw new InvalidOperationException("DAC did not become available.");
                    System.Threading.Thread.Sleep(500);
                }
            }
        }

        internal static void StartService()
        {
            using (var service = new ServiceController(ClientConfiguration.ServiceName))
            {
                if (service.Status == ServiceControllerStatus.Stopped) service.Start();
                service.WaitForStatus(ServiceControllerStatus.Running, TimeSpan.FromSeconds(30));
            }
        }
    }

    internal sealed class SettingsForm : Form
    {
        private readonly TextBox server = new TextBox();
        private readonly TextBox origin = new TextBox();
        private readonly Label status = new Label();

        internal SettingsForm()
        {
            Text = "SDP DAC 设置";
            ClientSize = new Size(570, 320);
            Font = new Font("Microsoft YaHei UI", 10);
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            AddLabel("SDP 服务地址（含端口）", 20, 20);
            server.SetBounds(20, 50, 530, 30);
            Controls.Add(server);
            AddLabel("SDP 网页来源（含端口）", 20, 95);
            origin.SetBounds(20, 125, 530, 30);
            Controls.Add(origin);
            AddLabel("支持 HTTP/HTTPS；HTTP 网页将配置 Chrome/Edge 本地访问权限。", 20, 165);
            status.SetBounds(20, 200, 530, 45);
            Controls.Add(status);
            var start = new Button { Text = "启动服务" };
            start.SetBounds(285, 260, 120, 35);
            start.Click += delegate { Run(delegate { Launcher.StartService(); RefreshStatus(); }); };
            Controls.Add(start);
            var save = new Button { Text = "保存并重启" };
            save.SetBounds(420, 260, 130, 35);
            save.Click += delegate { Run(SaveSettings); };
            Controls.Add(save);
            try
            {
                var settings = ClientConfiguration.Load(ClientConfiguration.DataRoot);
                server.Text = settings.ServerUrl;
                origin.Text = settings.WebOrigin;
            }
            catch (System.IO.FileNotFoundException) { }
            RefreshStatus();
        }

        private void AddLabel(string text, int x, int y)
        {
            var label = new Label { Text = text, AutoSize = true };
            label.Location = new Point(x, y);
            Controls.Add(label);
        }

        private void RefreshStatus()
        {
            try
            {
                using (var service = new ServiceController(ClientConfiguration.ServiceName))
                {
                    string port = "等待就绪（8280～8289）";
                    try { port = ClientConfiguration.RuntimeInfo(ClientConfiguration.DataRoot)["port"]; } catch (System.IO.IOException) { }
                    status.Text = "服务状态：" + service.Status + "  ·  本机端口：" + port;
                }
            }
            catch (InvalidOperationException) { status.Text = "服务尚未安装，请先运行安装包。"; }
        }

        private void SaveSettings()
        {
            var settings = new ClientConfiguration(server.Text, origin.Text);
            if (MessageBox.Show("保存设置会重启本机 DAC，正在执行的采集可能中断。是否继续？", "应用设置", MessageBoxButtons.YesNo, MessageBoxIcon.Warning) != DialogResult.Yes) return;
            var principal = new WindowsPrincipal(WindowsIdentity.GetCurrent());
            if (!principal.IsInRole(WindowsBuiltInRole.Administrator))
            {
                Process.Start(new ProcessStartInfo(Application.ExecutablePath,
                    "--apply-settings \"" + settings.ServerUrl + "\" \"" + settings.WebOrigin + "\"") { Verb = "runas", UseShellExecute = true });
                Close();
                return;
            }
            ApplyConfiguration(settings);
            RefreshStatus();
        }

        internal static void ApplyConfiguration(ClientConfiguration settings)
        {
            if (!new WindowsPrincipal(WindowsIdentity.GetCurrent()).IsInRole(WindowsBuiltInRole.Administrator))
                throw new UnauthorizedAccessException();
            using (var service = new ServiceController(ClientConfiguration.ServiceName))
            {
                if (service.Status != ServiceControllerStatus.Stopped)
                {
                    service.Stop();
                    service.WaitForStatus(ServiceControllerStatus.Stopped, TimeSpan.FromSeconds(60));
                }
                settings.Save(ClientConfiguration.DataRoot);
                BrowserAccessPolicy.Apply(settings);
                service.Start();
                service.WaitForStatus(ServiceControllerStatus.Running, TimeSpan.FromSeconds(30));
            }
        }

        private void Run(Action operation)
        {
            try { operation(); }
            catch (Exception failure)
            {
                MessageBox.Show(failure is ArgumentException ? failure.Message : "操作未完成，请检查服务或管理员权限。", Text, MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }
}
