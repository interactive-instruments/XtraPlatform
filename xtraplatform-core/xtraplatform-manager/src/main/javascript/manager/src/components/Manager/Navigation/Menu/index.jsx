import React from 'react';
import PropTypes from 'prop-types';

import { Box } from 'grommet';
import Link from './Link';

const NavigationMenu = ({ routes, onClick }) => {
    return (
        <Box flex='grow' justify='start'>
            {routes.map((route) => (
                // eslint-disable-next-line jsx-a11y/anchor-is-valid
                <Link
                    key={route.path}
                    path={route.path}
                    label={route.menuLabel}
                    onClick={onClick}
                />
            ))}
        </Box>
    );
};

NavigationMenu.displayName = 'NavigationMenu';

NavigationMenu.propTypes = {
    routes: PropTypes.arrayOf(PropTypes.object).isRequired,
    onClick: PropTypes.func,
};

NavigationMenu.defaultProps = {
    onClick: null,
};

export default NavigationMenu;
