import React, { useEffect, useState } from 'react';
import { NavigationContainer, DarkTheme } from '@react-navigation/native';
import { useNavigationContainerRef } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import { Platform, StyleSheet, View } from 'react-native';
import * as NavigationBar from 'expo-navigation-bar';
import { enableFreeze, enableScreens } from 'react-native-screens';

import HomeScreen from './src/screens/HomeScreen';
import StatsScreen from './src/screens/StatsScreen';
import HistoryScreen from './src/screens/HistoryScreen';
import AIScreen from './src/screens/AIScreen';
import CameraScanScreen from './src/screens/CameraScanScreen';
import FloatingBottomNav from './src/components/FloatingBottomNav';
import { colors } from './src/theme/colors';

const Stack = createNativeStackNavigator();
type MainRouteName = 'Home' | 'History' | 'AI' | 'Stats';

const MAIN_ROUTES: MainRouteName[] = ['Home', 'History', 'AI', 'Stats'];

enableScreens(true);
enableFreeze(true);

const appTheme = {
    ...DarkTheme,
    colors: {
        ...DarkTheme.colors,
        background: colors.background,
        card: colors.background,
        border: colors.border,
        primary: colors.primary,
        text: colors.text,
        notification: colors.primary,
    },
};

export default function App() {
    const navigationRef = useNavigationContainerRef();
    const [currentRoute, setCurrentRoute] = useState<MainRouteName>('Home');
    const [isMainRoute, setIsMainRoute] = useState(true);

    useEffect(() => {
        if (Platform.OS !== 'android') return;

        NavigationBar.setBackgroundColorAsync(colors.background).catch(() => {
        });
        NavigationBar.setButtonStyleAsync('light').catch(() => {
        });
        NavigationBar.setBorderColorAsync(colors.background).catch(() => {
        });
    }, []);

    const getActiveRoute = () => {
        const routeName = navigationRef.getCurrentRoute()?.name;
        return routeName;
    };

    const syncCurrentRoute = () => {
        const nextRoute = getActiveRoute();

        if (nextRoute && MAIN_ROUTES.includes(nextRoute as MainRouteName)) {
            setCurrentRoute((prevRoute) => (prevRoute === nextRoute ? prevRoute : (nextRoute as MainRouteName)));
            setIsMainRoute(true);
            return;
        }

        setIsMainRoute(false);
    };

    return (
        <SafeAreaProvider>
            <View style={styles.appRoot}>
                <NavigationContainer
                    ref={navigationRef}
                    theme={appTheme}
                    onReady={syncCurrentRoute}
                    onStateChange={syncCurrentRoute}
                >
                    <View style={styles.navigationRoot}>
                        <StatusBar style="light" />
                        <Stack.Navigator
                            screenOptions={{
                                headerShown: false,
                                animation: 'simple_push',
                                animationDuration: 180,
                                freezeOnBlur: true,
                                contentStyle: { backgroundColor: colors.background },
                            }}
                        >
                            <Stack.Screen name="Home" component={HomeScreen} />
                            <Stack.Screen name="History" component={HistoryScreen} />
                            <Stack.Screen name="AI" component={AIScreen} />
                            <Stack.Screen name="Stats" component={StatsScreen} />
                            <Stack.Screen
                                name="CameraScan"
                                component={CameraScanScreen}
                                options={{
                                    animation: 'slide_from_bottom',
                                    animationDuration: 220,
                                }}
                            />
                        </Stack.Navigator>
                        {isMainRoute ? (
                            <FloatingBottomNav currentRoute={currentRoute} navigation={navigationRef} />
                        ) : null}
                    </View>
                </NavigationContainer>
            </View>
        </SafeAreaProvider>
    );
}

const styles = StyleSheet.create({
    appRoot: {
        flex: 1,
        backgroundColor: colors.background,
    },
    navigationRoot: {
        flex: 1,
    },
});
