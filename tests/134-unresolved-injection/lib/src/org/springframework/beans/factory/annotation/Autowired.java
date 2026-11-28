package org.springframework.beans.factory.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Autowired {}
