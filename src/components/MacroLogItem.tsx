import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import Animated, { FadeOut, SlideInRight } from 'react-native-reanimated';
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
            entering={SlideInRight.duration(220).delay(Math.min(index * 35, 210))}
            exiting={FadeOut.duration(140)}
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
        padding: 14,
        marginVertical: 5,
        borderRadius: 10,
        borderWidth: 1,
        borderColor: colors.border,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 1 },
        shadowOpacity: 0.08,
        shadowRadius: 2,
        elevation: 1,
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
        backgroundColor: 'rgba(239, 68, 68, 0.12)',
    },
    deleteText: {
        color: colors.error,
        fontWeight: 'bold',
        fontSize: 16,
    },
});

export default MacroLogItem;
