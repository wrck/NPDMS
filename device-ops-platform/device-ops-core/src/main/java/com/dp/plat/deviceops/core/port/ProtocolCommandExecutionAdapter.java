package com.dp.plat.deviceops.core.port;

import com.dp.plat.deviceops.core.model.ConnectionProtocol;

public interface ProtocolCommandExecutionAdapter extends CommandExecutionPort {

    ConnectionProtocol protocol();
}
