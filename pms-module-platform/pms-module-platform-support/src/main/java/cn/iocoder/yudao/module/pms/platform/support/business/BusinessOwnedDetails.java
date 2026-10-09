package cn.iocoder.yudao.module.pms.platform.support.business;

import java.lang.annotation.*;

/** An owned child collection, persisted only inside its parent's authorized transaction. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface BusinessOwnedDetails {
    Class<? extends BusinessDetailMapper<?>> mapper();
}
