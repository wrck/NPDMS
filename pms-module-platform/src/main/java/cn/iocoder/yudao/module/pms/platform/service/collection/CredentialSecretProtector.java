package cn.iocoder.yudao.module.pms.platform.service.collection;

public interface CredentialSecretProtector {

    String protect(char[] secret);

    char[] reveal(String protectedSecret);
}
