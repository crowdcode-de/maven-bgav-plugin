package io.crowdcode.bgav;

import org.apache.maven.plugins.annotations.Parameter;

public class ModifyMojoModification
{
    @Parameter(required = true)
    private String expression;

    public String getExpression()
    {
        return expression;
    }

    @Parameter(required = true)
    private String value;

    public String getValue()
    {
        return value;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public void setValue(String value) {
        this.value = value;
    }

}
