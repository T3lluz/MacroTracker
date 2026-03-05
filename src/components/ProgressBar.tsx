import React, { useEffect } from 'react';
import { View, StyleSheet, Text } from 'react-native';
import Animated, {
    useSharedValue,
    useAnimatedStyle,
    withTiming,
    Easing,
} from 'react-native-reanimated';
import { colors } from '../theme/colors';

interface ProgressBarProps {
    progress: number; // 0 to 1
    color?: string;
    label?: string;
    height?: number;
}

const ProgressBar: React.FC<ProgressBarProps> = ({
    progress,
    color = colors.primary,
    label,
    height = 12,
}) => {
    const animatedProgress = useSharedValue(0);

    useEffect(() => {
        animatedProgress.value = withTiming(Math.min(1, Math.max(0, progress)), {
            duration: 420,
            easing: Easing.out(Easing.cubic),
        });
    }, [progress]);

    const animatedStyle = useAnimatedStyle(() => {
        return {
            width: `${animatedProgress.value * 100}%`,
        };
    });

    return (
        <View style={styles.container}>
            {label && <Text style={styles.label}>{label}</Text>}
            <View style={[styles.track, { height }]}>
                <Animated.View style={[styles.fill, { backgroundColor: color, height }, animatedStyle]} />
            </View>
        </View>
    );
};

const styles = StyleSheet.create({
    container: {
        marginVertical: 8,
    },
    label: {
        color: colors.textSecondary,
        marginBottom: 6,
        fontSize: 14,
        fontWeight: '500',
    },
    track: {
        backgroundColor: colors.border,
        borderRadius: 8,
        overflow: 'hidden',
        width: '100%',
    },
    fill: {
        position: 'absolute',
        left: 0,
        top: 0,
        borderRadius: 8,
    },
});

export default ProgressBar;
