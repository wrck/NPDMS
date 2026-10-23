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
                if (args.Length == 1 && args[0] == "--open-status")
                {
                    // Run in the installing user's UI session, never in the service or elevated execute sequence.
                    var deadline = DateTime.UtcNow.AddSeconds(60);
                    var address = "http://127.0.0.1:" + ClientConfiguration.Port + "/desktop-status";
                    while (true)
                    {
                        try
                        {
                            var request = System.Net.WebRequest.CreateHttp(address);
                            request.Proxy = null;
                            request.Timeout = 1500;
                            using (var response = request.GetResponse()) { }
                            break;
                        }
                        catch (System.Net.WebException)
                        {
                            if (DateTime.UtcNow >= deadline)
                                throw new InvalidOperationException("DAC did not become available.");
                            System.Threading.Thread.Sleep(500);
                        }
                    }
                    Process.Start(new ProcessStartInfo(address) { UseShellExecute = true });
                    return 0;
                }
                if (args.Length == 2 && args[0] == "--uri")
                {
                    if (!ClientConfiguration.IsStartUri(args[1])) return 2;
                    StartService();
                    return 0;
                }
                if (args.Length == 3 && args[0] == "--initialize")
                {
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
                if (args.Length > 0 && (args[0] == "--initialize" || args[0] == "--remove-browser-policy")) return 1;
                MessageBox.Show(failure is ArgumentException ? failure.Message : "DAC 操作失败，请检查服务安装状态，或联系管理员。", "NPDMS DAC", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return 1;
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
            Text = "NPDMS DAC 设置";
            ClientSize = new Size(570, 320);
            Font = new Font("Microsoft YaHei UI", 10);
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            AddLabel("NPDMS 服务地址（含端口）", 20, 20);
            server.SetBounds(20, 50, 530, 30);
            Controls.Add(server);
            AddLabel("NPDMS 网页来源（含端口）", 20, 95);
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
                    status.Text = "服务状态：" + service.Status + "  ·  本机端口：" + ClientConfiguration.Port;
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
