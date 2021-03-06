import { createFeature } from 'feature-u';
import { createTheme } from '@xtraplatform/core';

import themeDefault from './theme';
import { theme } from '../../feature-u';

export { themeDefault };

export default createFeature({
    name: 'theme-base',

    fassets: {
        // provided resources
        define: {
            // KEY: supply content under contract of the app feature
            [theme('default')]: createTheme(themeDefault),
        },
    },
});
