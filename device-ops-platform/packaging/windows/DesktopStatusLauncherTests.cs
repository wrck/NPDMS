using System;
using System.ComponentModel;
using System.Diagnostics;

namespace Npdms.Dac.Desktop
{
    // Explicit, read-only integration check against an already-running desktop. Never starts/stops a service or opens a browser.
    internal static class DesktopStatusLauncherTests
    {
        private static int Main(string[] args)
        {
            if (args.Length != 1) return 2;
            try
            {
                var endpoint = ClientConfiguration.RuntimeInfo(ClientConfiguration.DataRoot);
                bool oldQueryDenied = false;
                try { using (var process = Process.GetProcessById(Int32.Parse(endpoint["pid"]))) { bool exited = process.HasExited; } }
                catch (Win32Exception failure) { if (failure.NativeErrorCode != 5) throw; oldQueryDenied = true; }
                if (!oldQueryDenied) throw new InvalidOperationException("This regression requires an ordinary user denied the service process handle.");
                string address = Launcher.VerifiedStatusAddress(args[0]);
                if (address != "http://127.0.0.1:" + endpoint["port"] + "/desktop-status") throw new InvalidOperationException("Verified status address differs.");
                Console.WriteLine("PASS: old process query denied; fresh signed status verified without service/process privileges: " + address);
                return 0;
            }
            catch (Exception failure) { Console.Error.WriteLine("Status regression failed: " + failure.GetType().Name); return 1; }
        }
    }
}
