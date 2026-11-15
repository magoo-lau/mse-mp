package vip.mseapp.mp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mseapp.mp.entity.MpToken;

/** access_token 记录 Mapper。 */
@Mapper
public interface MpTokenMapper extends BaseMapper<MpToken> {
}
