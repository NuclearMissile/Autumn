package org.example.scan

import org.example.autumn.annotation.Component


@Component
class OuterBean {
    @Component
    class NestedBean
}
