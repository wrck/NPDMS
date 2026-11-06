using System;
using System.IO;
using System.Security.AccessControl;
using System.Security.Principal;
namespace Npdms.Dac.Desktop
{
    internal static class ServiceBridgeTests
    {
        private static FileSecurity Acl(WellKnownSidType owner, WellKnownSidType writer)
        {
            var acl = new FileSecurity();
            acl.SetOwner(new SecurityIdentifier(owner, null));
            acl.AddAccessRule(new FileSystemAccessRule(new SecurityIdentifier(writer, null), FileSystemRights.FullControl, AccessControlType.Allow));
            return acl;
        }
        private static void Reject(FileSecurity acl, bool service)
        {
            try { ServiceBridge.CheckAcl(acl, service); }
            catch (IOException) { return; }
            throw new Exception("Untrusted ACL accepted");
        }
        private static int Main()
        {
            ServiceBridge.CheckAcl(Acl(WellKnownSidType.LocalSystemSid, WellKnownSidType.BuiltinAdministratorsSid), false);
            ServiceBridge.CheckAcl(Acl(WellKnownSidType.LocalServiceSid, WellKnownSidType.LocalServiceSid), true);
            Reject(Acl(WellKnownSidType.LocalSystemSid, WellKnownSidType.BuiltinUsersSid), true);
            Reject(Acl(WellKnownSidType.BuiltinUsersSid, WellKnownSidType.LocalSystemSid), true);
            Reject(Acl(WellKnownSidType.LocalSystemSid, WellKnownSidType.LocalServiceSid), false);
            Console.WriteLine("Service bridge ACL tests passed on Windows");
            return 0;
        }
    }
}
