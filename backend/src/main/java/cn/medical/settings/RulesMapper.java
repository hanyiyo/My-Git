package cn.medical.settings;

import org.apache.ibatis.annotations.*;

@Mapper
public interface RulesMapper {
    @Select("SELECT CAST(body AS CHAR) FROM global_rules WHERE id=1") String body();
    @Insert("INSERT INTO global_rules(id,body,updated_at) VALUES(1,#{body},#{now}) ON DUPLICATE KEY UPDATE body=#{body},updated_at=#{now}")
    void save(@Param("body") String body, @Param("now") long now);
}
