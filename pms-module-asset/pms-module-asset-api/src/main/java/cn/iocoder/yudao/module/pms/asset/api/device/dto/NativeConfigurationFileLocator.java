package cn.iocoder.yudao.module.pms.asset.api.device.dto;

/** Durable identity for native configuration material; never an expiring download URL. */
public record NativeConfigurationFileLocator(Long configurationId,Long materialId) {
    public static final String PREFIX="pms-native-config:v1:";
    public NativeConfigurationFileLocator {
        if(configurationId==null||configurationId<=0||materialId==null||materialId<=0)throw new IllegalArgumentException("Invalid native configuration file identity");
    }
    @Override public String toString(){return PREFIX+configurationId+":"+materialId;}
    public static NativeConfigurationFileLocator parse(String value){
        if(value==null||!value.startsWith("pms-native-config:"))return null;
        if(!value.startsWith(PREFIX))throw new IllegalArgumentException("Unsupported native configuration file locator");
        var ids=value.substring(PREFIX.length()).split(":",-1);
        if(ids.length!=2)throw new IllegalArgumentException("Invalid native configuration file locator");
        return new NativeConfigurationFileLocator(Long.valueOf(ids[0]),Long.valueOf(ids[1]));
    }
}
