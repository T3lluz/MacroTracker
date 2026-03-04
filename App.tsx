import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';

import HomeScreen from './src/screens/HomeScreen';
import StatsScreen from './src/screens/StatsScreen';

const Stack = createNativeStackNavigator();

export default function App() {
    return (
        <SafeAreaProvider>
            <NavigationContainer>
                <StatusBar style="light" />
                <Stack.Navigator
                    screenOptions={{
                        headerShown: false,
                        animation: 'slide_from_right',
                    }}
                >
                    <Stack.Screen name="Home" component={HomeScreen} />
                    <Stack.Screen
                        name="Stats"
                        component={StatsScreen}
                        options={{
                            animation: 'slide_from_bottom'
                        }}
                    />
                </Stack.Navigator>
            </NavigationContainer>
        </SafeAreaProvider>
    );
}
