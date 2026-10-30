using System;
using System.Globalization;
using Microsoft.Win32;

namespace Npdms.Dac.Desktop
{
    internal static class BrowserAccessPolicy
    {
        internal const string OwnershipPath = @"Software\NPDMS\DAC\BrowserPolicyOwnership";
        internal const string ChromePath = @"Software\Policies\Google\Chrome\LocalNetworkAccessAllowedForUrls";
        internal const string EdgePath = @"Software\Policies\Microsoft\Edge\LocalNetworkAccessAllowedForUrls";

        internal static void Apply(ClientConfiguration configuration)
        {
            using (var machine = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, RegistryView.Registry64))
                Apply(machine, configuration);
        }

        internal static void Remove()
        {
            using (var machine = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, RegistryView.Registry64))
                Apply(machine, null);
        }

        // Official per-origin exemption, including HTTP origins; the URL includes its exact port.
        // https://learn.microsoft.com/en-us/deployedge/microsoft-edge-policies/LocalNetworkAccessAllowedForUrls
        // The injected root lets native tests use an isolated HKCU subtree, never browser policy keys.
        internal static void Apply(RegistryKey root, ClientConfiguration configuration)
        {
            string origin = configuration == null ? null : configuration.WebOrigin;
            if (origin != null)
            {
                var uri = new Uri(origin);
                if (uri.Scheme != "http" || uri.IsLoopback) origin = null;
            }
            foreach (string policyPath in new[] { ChromePath, EdgePath })
            {
                using (var policy = root.CreateSubKey(policyPath))
                using (var ownership = root.CreateSubKey(OwnershipPath + (policyPath == ChromePath ? "\\Chrome" : "\\Edge")))
                {
                    string oldName = ownership.GetValue("ValueName") as string;
                    string oldOrigin = ownership.GetValue("Origin") as string;
                    bool ownsCurrent = oldName != null && oldOrigin != null
                        && String.Equals(policy.GetValue(oldName) as string, oldOrigin, StringComparison.Ordinal);
                    if (ownsCurrent && oldOrigin == origin) continue;
                    if (ownsCurrent) policy.DeleteValue(oldName, false);
                    ownership.DeleteValue("ValueName", false);
                    ownership.DeleteValue("Origin", false);
                    if (origin == null) continue;

                    bool alreadyAllowed = false;
                    foreach (string name in policy.GetValueNames())
                        if (String.Equals(policy.GetValue(name) as string, origin, StringComparison.Ordinal)) alreadyAllowed = true;
                    // Existing organization/user entries remain theirs; uninstall must never delete them.
                    if (alreadyAllowed) continue;
                    int next = 1;
                    while (policy.GetValue(next.ToString(CultureInfo.InvariantCulture)) != null) next++;
                    string valueName = next.ToString(CultureInfo.InvariantCulture);
                    ownership.SetValue("ValueName", valueName, RegistryValueKind.String);
                    ownership.SetValue("Origin", origin, RegistryValueKind.String);
                    policy.SetValue(valueName, origin, RegistryValueKind.String);
                }
            }
        }
    }
}
