package com.dp.deviceops.core.port;

import com.dp.deviceops.core.model.ConnectionProtocol;

public interface ProtocolCommandExecutionAdapter extends CommandExecutionPort {

    ConnectionProtocol protocol();
}
