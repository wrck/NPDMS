package cn.iocoder.yudao.module.pms.customer.service.customer;

import cn.iocoder.yudao.module.pms.customer.service.customer.command.CreateCustomerCommand;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.CustomerCommandResult;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.CustomerLifecycleCommand;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.UpdateCustomerCommand;

public interface CustomerApplicationService {

    CustomerCommandResult create(CreateCustomerCommand command);

    CustomerCommandResult update(UpdateCustomerCommand command);

    CustomerCommandResult disable(CustomerLifecycleCommand command);

    CustomerCommandResult delete(CustomerLifecycleCommand command);

    CustomerCommandResult restore(CustomerLifecycleCommand command);
}
