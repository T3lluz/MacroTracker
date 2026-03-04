import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import Animated, { FadeIn, FadeOut, SlideInRight, SlideOutLeft } from 'react-native-reanimated';
import { DailyMacroLog } from '../types';
import { colors } from '../theme/colors';

interface MacroLogItemProps {
    item: DailyMacroLog;
    onDelete: (id: string) => void;
    index: number;
}

const MacroLogItem: React.FC<MacroLogItemProps> = ({ item, onDelete, index }) => {
    return (
        <Animated.View
            entering={SlideInRight.delay(index * 100).springify()}
            exiting={FadeOut.duration(200)}
            style={styles.container}
        >
            <View style={styles.content}>
                <Text style={styles.title}>{item.foodName || 'Meal'}</Text>
                <Text style={styles.details}>
                    {item.calories} kcal • {item.protein}g protein
                </Text>
            </View>
            <Pressable
                style={({ pressed }) => [
                    styles.deleteButton,
                    pressed && { opacity: 0.7 }
                ]}
                onPress={() => onDelete(item.id)}
            >
                <Text style={styles.deleteText}>✕</Text>
            </Pressable>
        </Animated.View>
    );
};

const styles = StyleSheet.create({
    container: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        backgroundColor: colors.surface,
        padding: 16,
        marginVertical: 6,
        borderRadius: 12,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.2,
        shadowRadius: 4,
        elevation: 3,
    },
    content: {
        flex: 1,
    },
    title: {
        color: colors.text,
        fontSize: 16,
        fontWeight: 'bold',
        marginBottom: 4,
    },
    details: {
        color: colors.textSecondary,
        fontSize: 14,
    },
    deleteButton: {
        padding: 8,
        borderRadius: 8,
        backgroundColor: 'rgba(207, 102, 121, 0.15)',
    },
    deleteText: {
        color: colors.error,
        fontWeight: 'bold',
        fontSize: 16,
    },
});

export default MacroLogItem;
