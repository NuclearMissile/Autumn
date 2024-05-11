package org.example.autumn.jdbc.orm.entity

interface EntityMixin {
    companion object {
        /**
         * Default big decimal storage type: DECIMAL(PRECISION, SCALE)
         *
         * Range = +/-999999999999999999.999999999999999999
         */
        const val PRECISION = 36

        /**
         * Default big decimal storage scale. Minimum is 0.000000000000000001.
         */
        const val SCALE = 18
        const val VAR_ENUM = 32
        const val VAR_CHAR_50 = 50
        const val VAR_CHAR_100 = 100
        const val VAR_CHAR_200 = 200
        const val VAR_CHAR_1000 = 1000
        const val VAR_CHAR_10000 = 10000
    }
}
