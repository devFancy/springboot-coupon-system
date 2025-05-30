package dev.be.coupon.api.coupon.infrastructure.redis.aop;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.ObjectUtils;

public class CustomSpringELParser {
    private CustomSpringELParser() {
    }

    public static Object getDynamicValue(final String[] parameterNames, final Object[] args, final String key) {
        if (ObjectUtils.isEmpty(key)) {
            return "";
        }

        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        return parser.parseExpression(key).getValue(context, Object.class);
    }
}
