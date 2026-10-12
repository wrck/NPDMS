package cn.iocoder.yudao.module.system.api.dept;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.PostDO;
import cn.iocoder.yudao.module.system.service.dept.PostService;
import cn.iocoder.yudao.module.system.dal.mysql.dept.PostMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;

/**
 * 岗位 API 实现类
 *
 * @author NPMS
 */
@Service
public class PostApiImpl implements PostApi {

    @Resource
    private PostService postService;

    @Resource
    private PostMapper postMapper;

    @Override
    public PostRespDTO getPostByCode(String code) {
        if (code == null || code.isBlank()) return null;
        return BeanUtils.toBean(postMapper.selectByCode(code), PostRespDTO.class);
    }

    @Override
    public void validPostList(Collection<Long> ids) {
        postService.validatePostList(ids);
    }

    @Override
    public List<PostRespDTO> getPostList(Collection<Long> ids) {
        List<PostDO> list = postService.getPostList(ids);
        return BeanUtils.toBean(list, PostRespDTO.class);
    }

}
