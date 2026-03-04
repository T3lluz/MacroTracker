import React from 'react';
import { Pressable, StyleSheet, Text, ViewStyle, TextStyle } from 'react-native';
import Animated, {
    useSharedValue,
    useAnimatedStyle,
    withSpring,
    withSequence,
    withTiming,
    Easing
} from 'react-native-reanimated';
import { colors } from '../theme/colors';

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

interface AnimatedButtonProps {
    onPress: () => void;
    title: string;
    style?: ViewStyle;
    textStyle?: TextStyle;
    variant?: 'primary' | 'secondary' | 'danger';
}

const AnimatedButton: React.FC<AnimatedButtonProps> = ({ onPress, title, style, textStyle, variant = 'primary' }) => {
    const scale = useSharedValue(1);
    const opacity = useSharedValue(1);

    const animatedStyle = useAnimatedStyle(() => {
        return {
            transform: [{ scale: scale.value }],
            opacity: opacity.value,
        };
    });

    const getBackgroundColor = () => {
        if (variant === 'danger') return colors.error;
        if (variant === 'secondary') return colors.secondary;
        return colors.primary;
    }

    const getTextColor = () => {
        if (variant === 'secondary') return '#000000';
        return '#FFFFFF';
    }

    const handlePressIn = () => {
        scale.value = withSpring(0.95, { stiffness: 400, damping: 20 });
        opacity.value = withTiming(0.8, { duration: 100 });
    };

    const handlePressOut = () => {
        scale.value = withSpring(1, { stiffness: 400, damping: 20 });
        opacity.value = withTiming(1, { duration: 100 });
    };

    return (
        <AnimatedPressable
            onPress={onPress}
            onPressIn={handlePressIn}
            onPressOut={handlePressOut}
            style={[
                styles.button,
                { backgroundColor: getBackgroundColor() },
                animatedStyle,
                style,
            ]}
        >
            <Text style={[styles.text, { color: getTextColor() }, textStyle]}>{title}</Text>
        </AnimatedPressable>
    );
};

const styles = StyleSheet.create({
    button: {
        paddingVertical: 14,
        paddingHorizontal: 24,
        borderRadius: 12,
        alignItems: 'center',
        justifyContent: 'center',
        marginVertical: 8,
        shadowColor: '#000',
        shadowOffset: {
            width: 0,
            height: 2,
        },
        shadowOpacity: 0.25,
        shadowRadius: 3.84,
        elevation: 5,
    },
    text: {
        fontSize: 16,
        fontWeight: '600',
    },
});

export default AnimatedButton;
