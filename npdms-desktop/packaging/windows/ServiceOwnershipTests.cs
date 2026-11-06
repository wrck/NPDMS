using System;
using System.IO;
namespace Npdms.Dac.Desktop
{
    internal static class ServiceOwnershipTests
    {
        private static int checks;
        private static void Reject(Action action)
        {
            try { action(); } catch (IOException) { checks++; return; }
            throw new Exception("Untrusted ownership accepted");
        }
        private static ServiceOwnership.ProcessIdentity Process(uint pid, uint parent, string image, long created)
        { return new ServiceOwnership.ProcessIdentity { Pid = pid, Parent = parent, Image = image, Created = created, Alive = true }; }
        private static byte[] Listener(uint pid, int port, uint address = 0x0100007f, uint count = 1)
        {
            var bytes = new byte[4 + 24 * count];
            Action<int, uint> write = (offset, value) => Buffer.BlockCopy(BitConverter.GetBytes(value), 0, bytes, offset, 4);
            write(0, count);
            for (int i = 0; i < count; i++) {
                int row = 4 + i * 24;
                write(row, 2); write(row + 4, address); write(row + 8, (uint)(((port & 255) << 8) | ((port >> 8) & 255))); write(row + 20, pid);
            }
            return bytes;
        }
        internal static void Run()
        {
            var service = Process(100, 4, @"C:\Program Files\SDP\DAC\SdpDacService.exe", 10);
            var host = Process(101, 100, @"C:\Program Files\SDP\DAC\SdpDacHost.exe", 11);
            var java = Process(102, 101, @"C:\Program Files\SDP\DAC\runtime\bin\java.exe", 12);
            Action validate = () => ServiceOwnership.ValidateChain(service, host, java,
                @"C:\Program Files\SDP\DAC\SdpDacService.exe", @"C:\Program Files\SDP\DAC\SdpDacHost.exe", @"C:\Program Files\SDP\DAC\runtime\bin\java.exe");
            validate(); checks++;
            host.Parent = 666; Reject(validate); host.Parent = 100;
            java.Parent = 666; Reject(validate); java.Parent = 101;
            host.Created = 9; Reject(validate); host.Created = 11;
            java.Created = 9; Reject(validate); java.Created = 12;
            java.Image = @"C:\Temp\java.exe"; Reject(validate); java.Image = @"C:\Program Files\SDP\DAC\runtime\bin\java.exe";
            java.Alive = false; Reject(validate); java.Alive = true;
            java.Pid = host.Pid; Reject(validate); java.Pid = 102;
            service.Pid = 0; Reject(validate); service.Pid = 100;
            service.Created = 0; Reject(validate); service.Created = 10;
            ServiceOwnership.ValidateListener(Listener(102, 8280), 102, 8280); checks++;
            ServiceOwnership.ValidateListener(Listener(102, 8289), 102, 8289); checks++;
            Reject(() => ServiceOwnership.ValidateListener(Listener(666, 8280), 102, 8280));
            Reject(() => ServiceOwnership.ValidateListener(Listener(102, 8280, 0), 102, 8280));
            Reject(() => ServiceOwnership.ValidateListener(Listener(102, 8280, 0x0101a8c0), 102, 8280));
            Reject(() => ServiceOwnership.ValidateListener(Listener(102, 8280, 0x0100007f, 2), 102, 8280));
            Reject(() => ServiceOwnership.ValidateListener(Listener(102, 8281), 102, 8280));
            Reject(() => ServiceOwnership.ValidateListener(Listener(102, 8280, 0x0100007f, 0), 102, 8280));
            Reject(() => ServiceOwnership.ValidateListener(new byte[] { 1, 0, 0, 0 }, 102, 8280));
            Reject(() => ServiceOwnership.ValidateListener(new byte[] { 255, 255, 255, 255 }, 102, 8280));
            Reject(() => ServiceOwnership.ValidateListener(Listener(102, 8280), 102, 8279));
            Console.WriteLine("Ownership decision checks passed: " + checks + "; no Windows API execution claimed");
        }
        private static int Main() { Run(); return 0; }
    }
}
