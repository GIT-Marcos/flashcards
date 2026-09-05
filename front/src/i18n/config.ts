import i18n from 'i18next';
import {initReactI18next} from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import en from './locales/en.json';
import es from './locales/es.json';
import {setLocale} from '@/lib/utils';

i18n
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
        resources: {
            en: en as unknown as Record<string, object>,
            es: es as unknown as Record<string, object>,
        },
        fallbackLng: 'en',
        defaultNS: 'common',
        interpolation: {
            escapeValue: false,
        },
        detection: {
            order: ['localStorage', 'navigator'],
            lookupLocalStorage: 'app_language',
            caches: ['localStorage'],
        },
    });

setLocale(i18n.language);

i18n.on('languageChanged', (lng) => {
    setLocale(lng);
});

export default i18n;
