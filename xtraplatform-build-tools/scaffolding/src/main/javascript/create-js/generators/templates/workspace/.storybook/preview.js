/* import React from 'react';
import { Grommet } from 'grommet';
import { themeBase } from '../src/components';

export const decorators = [
    (Story) => (
        <Grommet theme={themeBase}>
            <Story />
        </Grommet>
    ),
];*/

export const parameters = {
    actions: { argTypesRegex: '^on[A-Z].*' },
    controls: { expanded: true },
};
