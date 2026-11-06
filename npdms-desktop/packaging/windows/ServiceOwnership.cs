using System;
using System.IO;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

namespace Npdms.Dac.Desktop
{
    // Trust comes from SCM and live kernel process/socket ownership, not from
    // traversing the private ProgramData ACL. Query-only handles; no debug privilege.
    internal static class ServiceOwnership
    {
        internal sealed class ProcessIdentity
        {
            internal uint Pid, Parent;
            internal string Image;
            internal long Created;
            internal bool Alive;
        }
        internal static void ValidateChain(ProcessIdentity service, ProcessIdentity host, ProcessIdentity java,
            string serviceImage, string hostImage, string javaImage)
        {
            if (service == null || host == null || java == null || !service.Alive || !host.Alive || !java.Alive
                || service.Pid == 0 || host.Pid == 0 || java.Pid == 0
                || service.Pid == host.Pid || host.Pid == java.Pid || service.Pid == java.Pid
                || host.Parent != service.Pid || java.Parent != host.Pid
                || service.Created <= 0 || host.Created < service.Created || java.Created < host.Created
                || !String.Equals(service.Image, serviceImage, StringComparison.OrdinalIgnoreCase)
                || !String.Equals(host.Image, hostImage, StringComparison.OrdinalIgnoreCase)
                || !String.Equals(java.Image, javaImage, StringComparison.OrdinalIgnoreCase)) throw new IOException();
        }
        internal static void ValidateListener(byte[] table, uint pid, int port)
        {
            if (table == null || table.Length < 4 || pid == 0 || port < 8280 || port > 8289) throw new IOException();
            uint count = BitConverter.ToUInt32(table, 0);
            if (count > 65536 || 4L + count * 24L > table.Length) throw new IOException();
            int matches = 0;
            for (int i = 0; i < count; i++)
            {
                int offset = 4 + i * 24;
                uint networkPort = BitConverter.ToUInt32(table, offset + 8);
                int actualPort = (int)(((networkPort & 255) << 8) | ((networkPort >> 8) & 255));
                if (actualPort != port) continue;
                // Exactly the IPv4 loopback listener, never ANY_ADDRESS or a competing PID.
                if (BitConverter.ToUInt32(table, offset) != 2 || BitConverter.ToUInt32(table, offset + 4) != 0x0100007f
                    || BitConverter.ToUInt32(table, offset + 20) != pid) throw new IOException();
                matches++;
            }
            if (matches != 1) throw new IOException();
        }
        private sealed class KernelHandle : SafeHandleZeroOrMinusOneIsInvalid
        {
            private KernelHandle() : base(true) { }
            protected override bool ReleaseHandle() { return CloseHandle(handle); }
        }
        private sealed class ServiceHandle : SafeHandleZeroOrMinusOneIsInvalid
        {
            private ServiceHandle() : base(true) { }
            protected override bool ReleaseHandle() { return CloseServiceHandle(handle); }
        }
        [StructLayout(LayoutKind.Sequential)]
        private struct ServiceStatus { public uint Type, State, Accepted, Win32Exit, ServiceExit, Checkpoint, Wait, Pid, Flags; }
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct ProcessEntry
        {
            public uint Size, Usage, Pid;
            public UIntPtr Heap;
            public uint Module, Threads, Parent;
            public int Priority;
            public uint Flags;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 260)] public string Name;
        }
        [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern ServiceHandle OpenSCManager(string machine, string database, uint access);
        [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern ServiceHandle OpenService(ServiceHandle manager, string name, uint access);
        [DllImport("advapi32.dll", SetLastError = true)]
        private static extern bool QueryServiceStatusEx(ServiceHandle service, int level, out ServiceStatus status, int size, out int required);
        [DllImport("advapi32.dll")] private static extern bool CloseServiceHandle(IntPtr handle);
        [DllImport("kernel32.dll", SetLastError = true)] private static extern KernelHandle CreateToolhelp32Snapshot(uint flags, uint pid);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)] private static extern bool Process32FirstW(KernelHandle snapshot, ref ProcessEntry entry);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)] private static extern bool Process32NextW(KernelHandle snapshot, ref ProcessEntry entry);
        [DllImport("kernel32.dll", SetLastError = true)] private static extern KernelHandle OpenProcess(uint access, bool inherit, uint pid);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)] private static extern bool QueryFullProcessImageName(KernelHandle process, uint flags, StringBuilder name, ref int size);
        [DllImport("kernel32.dll", SetLastError = true)] private static extern bool GetProcessTimes(KernelHandle process, out long created, out long exited, out long kernel, out long user);
        [DllImport("kernel32.dll", SetLastError = true)] private static extern bool GetExitCodeProcess(KernelHandle process, out uint code);
        [DllImport("kernel32.dll")] private static extern bool CloseHandle(IntPtr handle);
        [DllImport("iphlpapi.dll")] private static extern uint GetExtendedTcpTable(IntPtr table, ref int size, bool order, uint family, int tableClass, uint reserved);
        private static void NativeError(int code)
        {
            if (code == 5) throw new UnauthorizedAccessException();
            throw new IOException();
        }
        private static uint ServicePid(ServiceHandle service)
        {
            ServiceStatus status; int required;
            if (!QueryServiceStatusEx(service, 0, out status, Marshal.SizeOf(typeof(ServiceStatus)), out required)) NativeError(Marshal.GetLastWin32Error());
            // SCM documents valid PID in RUNNING; pending/stopped values are not usable.
            if (status.State != 4 || status.Pid == 0 || status.Type != 0x10) throw new IOException();
            return status.Pid;
        }
        private static Dictionary<uint, ProcessEntry> Processes()
        {
            using (var handle = CreateToolhelp32Snapshot(2, 0))
            {
                if (handle.IsInvalid) NativeError(Marshal.GetLastWin32Error());
                var entry = new ProcessEntry { Size = (uint)Marshal.SizeOf(typeof(ProcessEntry)) };
                if (!Process32FirstW(handle, ref entry)) NativeError(Marshal.GetLastWin32Error());
                var result = new Dictionary<uint, ProcessEntry>();
                do {
                    if (result.Count >= 65536 || result.ContainsKey(entry.Pid)) throw new IOException();
                    result.Add(entry.Pid, entry);
                    entry.Size = (uint)Marshal.SizeOf(typeof(ProcessEntry));
                } while (Process32NextW(handle, ref entry));
                if (Marshal.GetLastWin32Error() != 18) NativeError(Marshal.GetLastWin32Error());
                return result;
            }
        }
        private static KernelHandle ProcessHandle(uint pid)
        {
            var handle = OpenProcess(0x1000, false, pid); // PROCESS_QUERY_LIMITED_INFORMATION only.
            if (handle.IsInvalid) { int error = Marshal.GetLastWin32Error(); handle.Dispose(); NativeError(error); }
            return handle;
        }
        private static ProcessIdentity Identity(KernelHandle handle, ProcessEntry entry)
        {
            long created = 0, exited = 0, kernel = 0, user = 0; uint code = 0;
            var image = new StringBuilder(32768); int size = image.Capacity;
            if (!QueryFullProcessImageName(handle, 0, image, ref size) || !GetProcessTimes(handle, out created, out exited, out kernel, out user)
                || !GetExitCodeProcess(handle, out code)) NativeError(Marshal.GetLastWin32Error());
            return new ProcessIdentity { Pid = entry.Pid, Parent = entry.Parent, Image = image.ToString(), Created = created, Alive = code == 259 && exited == 0 };
        }
        private static byte[] Listeners()
        {
            int size = 0;
            uint code = GetExtendedTcpTable(IntPtr.Zero, ref size, false, 2, 3, 0);
            if (code != 122 || size < 4 || size > 4 + 65536 * 24) NativeError((int)code);
            for (int attempt = 0; attempt < 3; attempt++)
            {
                if (size < 4 || size > 4 + 65536 * 24) throw new IOException();
                int capacity = size; IntPtr buffer = Marshal.AllocHGlobal(capacity);
                try {
                    code = GetExtendedTcpTable(buffer, ref size, false, 2, 3, 0);
                    if (code == 122) continue;
                    if (code != 0) NativeError((int)code);
                    if (size < 4 || size > capacity) throw new IOException();
                    var bytes = new byte[size]; Marshal.Copy(buffer, bytes, 0, size); return bytes;
                } finally { Marshal.FreeHGlobal(buffer); }
            }
            throw new IOException();
        }
        internal static void Verify(string serviceName, string serviceImage, uint javaPid, int port)
        {
            using (var manager = OpenSCManager(null, null, 1)) // SC_MANAGER_CONNECT
            {
                if (manager.IsInvalid) NativeError(Marshal.GetLastWin32Error());
                using (var service = OpenService(manager, serviceName, 4)) // SERVICE_QUERY_STATUS
                {
                    if (service.IsInvalid) NativeError(Marshal.GetLastWin32Error());
                    uint servicePid = ServicePid(service);
                    var processes = Processes(); ProcessEntry root, host, java;
                    if (!processes.TryGetValue(servicePid, out root) || !processes.TryGetValue(javaPid, out java)
                        || !processes.TryGetValue(java.Parent, out host)) throw new IOException();
                    using (var rootHandle = ProcessHandle(root.Pid))
                    using (var hostHandle = ProcessHandle(host.Pid))
                    using (var javaHandle = ProcessHandle(java.Pid))
                    {
                        // The first snapshot can race process exit. With all three handles
                        // retained, take parent IDs again before trusting the chain.
                        processes = Processes();
                        if (!processes.TryGetValue(servicePid, out root) || !processes.TryGetValue(javaPid, out java)
                            || !processes.TryGetValue(host.Pid, out host)) throw new IOException();
                        string install = Path.GetDirectoryName(serviceImage);
                        string hostImage = Path.Combine(install, "SdpDacHost.exe");
                        string javaImage = Path.Combine(install, "runtime", "bin", "java.exe");
                        ValidateChain(Identity(rootHandle, root), Identity(hostHandle, host), Identity(javaHandle, java), serviceImage, hostImage, javaImage);
                        ValidateListener(Listeners(), javaPid, port);
                        // Retained handles and creation order reject PID reuse; recheck alive/SCM after table lookup.
                        if (ServicePid(service) != servicePid) throw new IOException();
                        ValidateChain(Identity(rootHandle, root), Identity(hostHandle, host), Identity(javaHandle, java), serviceImage, hostImage, javaImage);
                    }
                }
            }
        }
    }
}
