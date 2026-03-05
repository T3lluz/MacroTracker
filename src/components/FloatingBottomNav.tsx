import React, { useEffect, useMemo, useRef } from 'react';
import { LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Animated, {
    Easing,
    useAnimatedStyle,
    useSharedValue,
    withTiming,
} from 'react-native-reanimated';
import { colors } from '../theme/colors';
import { hapticLight } from '../utils/haptics';

type RouteName = 'Home' | 'History' | 'AI' | 'Stats';

interface FloatingBottomNavProps {
    currentRoute: RouteName;
    navigation: any;
}

const NAV_ITEMS: Array<{
    route: RouteName;
    label: string;
    icon: keyof typeof Ionicons.glyphMap;
    activeIcon: keyof typeof Ionicons.glyphMap;
}> = [
    { route: 'Home', label: 'Log', icon: 'home-outline', activeIcon: 'home' },
    { route: 'History', label: 'History', icon: 'stats-chart-outline', activeIcon: 'stats-chart' },
    { route: 'AI', label: 'AI', icon: 'sparkles-outline', activeIcon: 'sparkles' },
    { route: 'Stats', label: 'More', icon: 'grid-outline', activeIcon: 'grid' },
];

const PILL_PADDING = 5;
const INDICATOR_TIMING = {
    duration: 180,
    easing: Easing.out(Easing.cubic),
};

const FloatingBottomNav: React.FC<FloatingBottomNavProps> = ({ currentRoute, navigation }) => {
    const insets = useSafeAreaInsets();
    const activeIndex = useMemo(
        () => NAV_ITEMS.findIndex((item) => item.route === currentRoute),
        [currentRoute],
    );
    const indicatorX = useSharedValue(0);
    const indicatorWidth = useSharedValue(0);
    const [pillWidth, setPillWidth] = React.useState(0);
    const lastMeasuredWidthRef = useRef(0);

    useEffect(() => {
        if (!pillWidth) return;

        const tabWidth = (pillWidth - PILL_PADDING * 2) / NAV_ITEMS.length;
        indicatorWidth.value = tabWidth;
        indicatorX.value = withTiming(PILL_PADDING + tabWidth * Math.max(activeIndex, 0), INDICATOR_TIMING);
    }, [pillWidth, activeIndex]);

    const animatedIndicatorStyle = useAnimatedStyle(() => ({
        transform: [{ translateX: indicatorX.value }],
        width: indicatorWidth.value,
    }));

    const handlePillLayout = (event: LayoutChangeEvent) => {
        const nextWidth = Math.round(event.nativeEvent.layout.width);
        if (nextWidth !== lastMeasuredWidthRef.current) {
            lastMeasuredWidthRef.current = nextWidth;
            setPillWidth(nextWidth);
        }
    };

    return (
        <View pointerEvents="box-none" style={styles.wrapper}>
            <Animated.View
                onLayout={handlePillLayout}
                renderToHardwareTextureAndroid
                shouldRasterizeIOS
                style={[styles.pill, { marginBottom: Math.max(insets.bottom, 12) }]}
            >
                <Animated.View
                    pointerEvents="none"
                    renderToHardwareTextureAndroid
                    shouldRasterizeIOS
                    style={[styles.activeIndicator, animatedIndicatorStyle]}
                />
                {NAV_ITEMS.map((item) => {
                    const isActive = item.route === currentRoute;
                    return (
                        <Pressable
                            key={item.route}
                            onPress={() => {
                                if (!isActive) {
                                    hapticLight();
                                    navigation.navigate(item.route);
                                }
                            }}
                            style={({ pressed }) => [styles.item, pressed && styles.itemPressed]}
                        >
                            <Ionicons
                                name={isActive ? item.activeIcon : item.icon}
                                size={18}
                                color={isActive ? '#FFFFFF' : colors.textSecondary}
                            />
                            <Text style={[styles.label, isActive && styles.activeLabel]}>
                                {item.label}
                            </Text>
                        </Pressable>
                    );
                })}
            </Animated.View>
        </View>
    );
};

const styles = StyleSheet.create({
    wrapper: {
        position: 'absolute',
        left: 0,
        right: 0,
        bottom: 0,
        alignItems: 'center',
    },
    pill: {
        width: '92%',
        maxWidth: 460,
        backgroundColor: colors.surface,
        borderRadius: 999,
        borderWidth: 1,
        borderColor: colors.border,
        padding: PILL_PADDING,
        flexDirection: 'row',
        alignItems: 'stretch',
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.15,
        shadowRadius: 12,
        elevation: 6,
        overflow: 'hidden',
    },
    activeIndicator: {
        position: 'absolute',
        top: PILL_PADDING,
        bottom: PILL_PADDING,
        left: 0,
        borderRadius: 999,
        backgroundColor: colors.primary,
    },
    item: {
        flexBasis: 0,
        flexGrow: 1,
        flexShrink: 1,
        borderRadius: 999,
        paddingHorizontal: 4,
        paddingVertical: 9,
        alignItems: 'center',
        justifyContent: 'center',
        flexDirection: 'row',
        zIndex: 1,
    },
    itemPressed: {
        opacity: 0.8,
    },
    label: {
        fontSize: 13,
        fontWeight: '600',
        color: colors.textSecondary,
        marginLeft: 6,
    },
    activeLabel: {
        color: '#FFFFFF',
    },
});

export default FloatingBottomNav;
