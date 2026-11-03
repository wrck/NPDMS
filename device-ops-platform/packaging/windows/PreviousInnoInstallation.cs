using System;
using System.Diagnostics;
using System.IO;
using System.Text.RegularExpressions;
using Microsoft.Win32;

namespace Npdms.Dac.Desktop
{
    internal sealed class PreviousInnoInstallation
    {
        internal const string RegistrationKey = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\NPDMS-DAC-Inno_is1";
        internal string Executable;
        internal string Directory;
        internal Version Version;

        internal sealed class Registration
        {
            internal string InstallDirectory, UninstallCommand, DisplayVersion, TargetVersion;
            internal string[] ExpectedDirectories;
        }

        internal static PreviousInnoInstallation Parse(Registration registration)
        {
            Version oldVersion, targetVersion;
            if (!System.Version.TryParse(registration.DisplayVersion, out oldVersion)
                || !System.Version.TryParse(registration.TargetVersion, out targetVersion) || oldVersion > targetVersion)
                throw new InvalidOperationException("已安装版本无法确认或高于新版本，不能自动卸载。");
            string directory = Path.GetFullPath(registration.InstallDirectory).TrimEnd(Path.DirectorySeparatorChar);
            if (!Array.Exists(registration.ExpectedDirectories, expected => String.Equals(directory,
                Path.GetFullPath(expected).TrimEnd(Path.DirectorySeparatorChar), StringComparison.OrdinalIgnoreCase)))
                throw new InvalidOperationException("旧版安装目录不符合预期，不能自动卸载。");
            var command = Regex.Match(registration.UninstallCommand ?? "", "\\A\"([^\"]+)\"\\s*\\z");
            if (!command.Success) throw new InvalidOperationException("旧版卸载入口无效。");
            string executable = Path.GetFullPath(command.Groups[1].Value);
            if (!Regex.IsMatch(Path.GetFileName(executable), "\\Aunins[0-9]{3}\\.exe\\z", RegexOptions.IgnoreCase)
                || !String.Equals(Path.GetDirectoryName(executable), directory, StringComparison.OrdinalIgnoreCase))
                throw new InvalidOperationException("旧版卸载入口不属于 DAC 安装目录。");
            return new PreviousInnoInstallation { Executable = executable, Directory = directory, Version = oldVersion };
        }

        internal static PreviousInnoInstallation Read(string targetVersion)
        {
            using (var machine = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, RegistryView.Registry64))
            using (var key = machine.OpenSubKey(RegistrationKey))
            {
                if (key == null) return null;
                return Parse(new Registration {
                    InstallDirectory = key.GetValue("InstallLocation") as string,
                    UninstallCommand = key.GetValue("UninstallString") as string,
                    DisplayVersion = key.GetValue("DisplayVersion") as string,
                    ExpectedDirectories = new[] { ClientConfiguration.InstallDirectory, ClientConfiguration.LegacyInstallDirectory },
                    TargetVersion = targetVersion
                });
            }
        }

        internal sealed class Removal
        {
            internal Func<ProcessStartInfo, int> Run;
            internal Func<bool> RegistrationExists;
            internal Func<bool> ServiceExists;
        }

        internal void Remove(Removal removal)
        {
            int exit = removal.Run(new ProcessStartInfo(Executable, "/VERYSILENT /SUPPRESSMSGBOXES /NORESTART")
                { UseShellExecute = false, CreateNoWindow = true, WorkingDirectory = Path.GetTempPath() });
            if (exit != 0) throw new InvalidOperationException("旧版 DAC 卸载退出码 " + exit + "，新版安装已中止，数据目录保留。");
            if (removal.RegistrationExists() || removal.ServiceExists())
                throw new InvalidOperationException("旧版 DAC 尚未完整卸载，新版安装已中止，数据目录保留。");
        }
    }
}
