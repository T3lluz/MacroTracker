import { Platform } from 'react-native';
import * as Haptics from 'expo-haptics';

const canHaptic = Platform.OS === 'ios' || Platform.OS === 'android';
const HAPTIC_COOLDOWN_MS = 120;

let lastHapticAt = 0;

type HapticKind = 'light' | 'medium' | 'success' | 'error';

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const runIOSPattern = async (kind: HapticKind) => {
    if (kind === 'success') {
        await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
        return;
    }

    if (kind === 'error') {
        await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
        return;
    }

    await Haptics.impactAsync(
        kind === 'medium'
            ? Haptics.ImpactFeedbackStyle.Medium
            : Haptics.ImpactFeedbackStyle.Light,
    );
};

const runAndroidPattern = async (kind: HapticKind) => {
    if (kind === 'light') {
        await Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Keyboard_Tap);
        return;
    }

    if (kind === 'medium') {
        await Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Confirm);
        return;
    }

    if (kind === 'success') {
        await Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Confirm);
        await sleep(45);
        await Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Context_Click);
        return;
    }

    await Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Reject);
};

const triggerKnock = async (kind: HapticKind, force = false) => {
    if (!canHaptic) return;

    const now = Date.now();
    if (!force && now - lastHapticAt < HAPTIC_COOLDOWN_MS) {
        return;
    }

    try {
        if (Platform.OS === 'android') {
            await runAndroidPattern(kind);
        } else {
            await runIOSPattern(kind);
        }
        lastHapticAt = Date.now();
    } catch {
        try {
            await Haptics.impactAsync(
                kind === 'medium' || kind === 'success'
                    ? Haptics.ImpactFeedbackStyle.Medium
                    : Haptics.ImpactFeedbackStyle.Light,
            );
            lastHapticAt = Date.now();
        } catch {
        }
    }
};

export const hapticLight = async () => {
    await triggerKnock('light');
};

export const hapticMedium = async () => {
    await triggerKnock('medium', true);
};

export const hapticSuccess = async () => {
    await triggerKnock('success', true);
};

export const hapticError = async () => {
    await triggerKnock('error', true);
};

