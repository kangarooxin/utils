# utils

# UniqueColumnPlugin
## config xml
```
<plugin type="support.mybatis.UniqueColumnPlugin"/>

<table tableName="test" domainObjectName="Test">
    <property name="uniqueColumns" value="type,status|name"/>
</table>
```
## result
```
Test selectByTypeAndStatus(@Param("type") Byte type, @Param("status") Byte status);

Test selectByName(String name);
```

```
<select id="selectByTypeAndStatus" parameterType="map" resultMap="BaseResultMap">
  select 
  <include refid="Base_Column_List" />
  from test
  where type = #{type,jdbcType=TINYINT}
    and status = #{status,jdbcType=TINYINT}
  limit 1
</select>
<select id="selectByName" parameterType="java.lang.String" resultMap="BaseResultMap">
  select 
  <include refid="Base_Column_List" />
  from test
  where name = #{name,jdbcType=VARCHAR}
  limit 1
</select>
```
