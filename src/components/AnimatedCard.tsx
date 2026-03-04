import React, { useEffect } from 'react';
import { ViewStyle, StyleSheet, Dimensions } from 'react-native';
import Animated, {
    useSharedValue,
    useAnimatedStyle,
    withSpring,
    withDelay,
} from 'react-native-reanimated';
import { colors } from '../theme/colors';

interface AnimatedCardProps {
    children: React.ReactNode;
    style?: ViewStyle;
    delay?: number;
}

const { width } = Dimensions.get('window');

const AnimatedCard: React.FC<AnimatedCardProps> = ({ children, style, delay = 0 }) => {
    const translateX = useSharedValue(width);
    const opacity = useSharedValue(0);

    useEffect(() => {
        translateX.value = withDelay(delay, withSpring(0, { damping: 15, stiffness: 100 }));
        opacity.value = withDelay(delay, withSpring(1));
    }, []);

    const animatedStyle = useAnimatedStyle(() => {
        return {
            transform: [{ translateX: translateX.value }],
            opacity: opacity.value,
        };
    });

    return (
        <Animated.View style={[styles.card, animatedStyle, style]}>
            {children}
        </Animated.View>
    );
};

const styles = StyleSheet.create({
    card: {
        backgroundColor: colors.surface,
        borderRadius: 16,
        padding: 16,
        marginVertical: 8,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.3,
        shadowRadius: 5,
        elevation: 8,
    },
});

export default AnimatedCard;
