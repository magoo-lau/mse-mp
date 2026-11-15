package vip.mseapp.mp.openapi;

import vip.mseapp.open.api.OpenApiContributor;
import vip.mseapp.open.api.OpenApiDescriptor;

import java.util.List;

/**
 * 公众号开放能力声明（开放平台自注册：应用就绪后由 open-api 推送器推送至 mse-open 目录）。
 */
public class MpOpenApiContributor implements OpenApiContributor {

    private static final String FAN_CONTRACT = "vip.mseapp.mp.api.RemoteMpFanService";
    private static final String MESSAGE_CONTRACT = "vip.mseapp.mp.api.RemoteMpMessageService";

    @Override
    public List<OpenApiDescriptor> descriptors() {
        return List.of(
                descriptor("open:mp:fans-page", "查询粉丝", "GET", "/open/api/mp/fans",
                        FAN_CONTRACT + "#pageFans",
                        "query: accountId/subscribed/keyword/pageNum/pageSize；粉丝由回调事件自动维护"),
                descriptor("open:mp:messages-page", "查询消息流水", "GET", "/open/api/mp/messages",
                        MESSAGE_CONTRACT + "#pageMessages",
                        "query: accountId/direction/kind/type/keyword/pageNum/pageSize"));
    }

    private static OpenApiDescriptor descriptor(String code, String name, String method, String path,
                                                String target, String description) {
        OpenApiDescriptor item = new OpenApiDescriptor();
        item.setCode(code);
        item.setName(name);
        item.setHttpMethod(method);
        item.setPath(path);
        item.setTarget(target);
        item.setDescription(description);
        return item;
    }
}
