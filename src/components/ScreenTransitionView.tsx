import React from 'react';
import { View, ViewStyle } from 'react-native';

interface ScreenTransitionViewProps {
    children: React.ReactNode;
    style?: ViewStyle;
}

const ScreenTransitionView: React.FC<ScreenTransitionViewProps> = React.memo(({ children, style }) => {
    return <View style={style}>{children}</View>;
});

export default ScreenTransitionView;

