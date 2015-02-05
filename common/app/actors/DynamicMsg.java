package actors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;

@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include= JsonTypeInfo.As.PROPERTY, property = "type")
public class DynamicMsg implements Serializable {
    public String msg;

    @JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include= JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "paramsType")
    public Object params;

    public DynamicMsg(@JsonProperty("msg") String m, @JsonProperty("params") Object p) { msg = m; params = p; }
}
