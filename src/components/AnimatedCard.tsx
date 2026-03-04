import React, { useEffect } from 'react';
import { ViewStyle, StyleSheet } from 'react-native';
import Animated, {
    useSharedValue,
    useAnimatedStyle,
    withTiming,
    withDelay,
    Easing,
} from 'react-native-reanimated';
import { colors } from '../theme/colors';

interface AnimatedCardProps {
    children: React.ReactNode;
    style?: ViewStyle;
    delay?: number;
}

const AnimatedCard: React.FC<AnimatedCardProps> = ({ children, style, delay = 0 }) => {
    const progress = useSharedValue(0);

    useEffect(() => {
        progress.value = 0;
        progress.value = withDelay(
            delay,
            withTiming(1, {
                duration: 240,
                easing: Easing.out(Easing.cubic),
            }),
        );
    }, [delay]);

    const animatedStyle = useAnimatedStyle(() => {
        return {
            transform: [{ translateY: (1 - progress.value) * 12 }],
            opacity: progress.value,
        };
    });

    return (
        <Animated.View
            renderToHardwareTextureAndroid
            shouldRasterizeIOS
            style={[styles.card, animatedStyle, style]}
        >
            {children}
        </Animated.View>
    );
};

const styles = StyleSheet.create({
    card: {
        backgroundColor: colors.surface,
        borderRadius: 14,
        padding: 14,
        marginVertical: 6,
        borderWidth: 1,
        borderColor: colors.border,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.12,
        shadowRadius: 4,
        elevation: 2,
    },
});

export default AnimatedCard;
