import React from 'react';
import { Pressable, StyleSheet, Text, ViewStyle, TextStyle } from 'react-native';
import Animated, {
    useSharedValue,
    useAnimatedStyle,
    withSpring,
    withTiming,
} from 'react-native-reanimated';
import { colors } from '../theme/colors';
import { hapticLight, hapticMedium } from '../utils/haptics';

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

interface AnimatedButtonProps {
    onPress: () => void;
    title: string;
    style?: ViewStyle;
    textStyle?: TextStyle;
    variant?: 'primary' | 'secondary' | 'danger';
    haptic?: 'none' | 'light' | 'medium';
}

const AnimatedButton: React.FC<AnimatedButtonProps> = ({
    onPress,
    title,
    style,
    textStyle,
    variant = 'primary',
    haptic = 'light',
}) => {
    const scale = useSharedValue(1);
    const opacity = useSharedValue(1);

    const animatedStyle = useAnimatedStyle(() => {
        return {
            transform: [{ scale: scale.value }],
            opacity: opacity.value,
        };
    });

    const getVariantStyles = () => {
        if (variant === 'danger') {
            return {
                backgroundColor: colors.error,
                borderColor: colors.error,
                textColor: '#FFFFFF',
            };
        }

        if (variant === 'secondary') {
            return {
                backgroundColor: colors.surface,
                borderColor: colors.border,
                textColor: colors.text,
            };
        }

        return {
            backgroundColor: colors.primary,
            borderColor: colors.primary,
            textColor: '#FFFFFF',
        };
    };

    const variantStyles = getVariantStyles();

    const handlePressIn = () => {
        scale.value = withSpring(0.95, { stiffness: 400, damping: 20 });
        opacity.value = withTiming(0.8, { duration: 100 });
    };

    const handlePressOut = () => {
        scale.value = withSpring(1, { stiffness: 400, damping: 20 });
        opacity.value = withTiming(1, { duration: 100 });
    };

    const handlePress = () => {
        if (haptic === 'light') {
            hapticLight();
        } else if (haptic === 'medium') {
            hapticMedium();
        }
        onPress();
    };

    return (
        <AnimatedPressable
            onPress={handlePress}
            onPressIn={handlePressIn}
            onPressOut={handlePressOut}
            style={[
                styles.button,
                {
                    backgroundColor: variantStyles.backgroundColor,
                    borderColor: variantStyles.borderColor,
                },
                animatedStyle,
                style,
            ]}
        >
            <Text style={[styles.text, { color: variantStyles.textColor }, textStyle]}>{title}</Text>
        </AnimatedPressable>
    );
};

const styles = StyleSheet.create({
    button: {
        paddingVertical: 14,
        paddingHorizontal: 24,
        borderRadius: 10,
        borderWidth: 1,
        alignItems: 'center',
        justifyContent: 'center',
        marginVertical: 6,
        shadowColor: '#000',
        shadowOffset: {
            width: 0,
            height: 1,
        },
        shadowOpacity: 0.1,
        shadowRadius: 2,
        elevation: 1,
    },
    text: {
        fontSize: 16,
        fontWeight: '600',
    },
});

export default AnimatedButton;
