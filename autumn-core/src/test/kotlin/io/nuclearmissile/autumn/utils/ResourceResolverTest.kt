package io.nuclearmissile.autumn.utils

import kotlin.test.Test
import kotlin.test.assertTrue


class ResourceResolverTest {
    @Test
    fun scanClass() {
        val pkg = "io.nuclearmissile.scan"
        val classes = ResourceResolver(pkg).scanResources { res ->
            val name = res.name
            if (name.endsWith(".class"))
                name.substring(0, name.length - 6).replace("/", ".").replace("\\", ".")
            else null
        }.sorted()
        val listClasses = arrayOf(
            // list of some scan classes:
            "io.nuclearmissile.scan.ValueConverterBean",
            "io.nuclearmissile.scan.AnnotationDestroyBean",
            "io.nuclearmissile.scan.SpecifyInitConfiguration",
            "io.nuclearmissile.scan.OriginBean",
            "io.nuclearmissile.scan.FirstProxyBeanPostProcessor",
            "io.nuclearmissile.scan.SecondProxyBeanPostProcessor",
            "io.nuclearmissile.scan.OuterBean",
            "io.nuclearmissile.scan.OuterBean\$NestedBean",
            "io.nuclearmissile.scan.sub1.Sub1Bean",
            "io.nuclearmissile.scan.sub1.sub2.Sub2Bean",
            "io.nuclearmissile.scan.sub1.sub2.sub3.Sub3Bean",
        )
        for (clazz in listClasses) {
            assertTrue(classes.contains(clazz))
        }
    }
}