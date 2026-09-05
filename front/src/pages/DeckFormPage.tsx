import {useEffect, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {createDeck, getDecks, updateDeck} from '@/api/decks.api';
import {generateDeckFromFile, generateDeckFromTopic} from '@/api/ai.api';
import {Input} from '@/components/ui/Input';
import {Select} from '@/components/ui/Select';
import {Button} from '@/components/ui/Button';
import {Modal} from '@/components/ui/Modal';
import {aiTopicSchema, type DeckFormData, deckSchema, translateFieldErrors} from '@/lib/validators';
import {sanitizeHtml} from '@/lib/sanitize';
import {toast} from 'sonner';
import {useTranslation} from 'react-i18next';
import type {DeckResponse} from '@/types/deck.types';

const AI_PROVIDERS: Array<{ value: string; labelKey: string }> = [
    {value: 'OPENAI', labelKey: 'ai:providerOpenai'},
    {value: 'ANTHROPIC', labelKey: 'ai:providerAnthropic'},
    {value: 'GOOGLE', labelKey: 'ai:providerGoogle'},
    {value: 'MISTRAL', labelKey: 'ai:providerMistral'},
    {value: 'OPENROUTER', labelKey: 'ai:providerOpenrouter'},
];

const MODEL_DEFAULTS: Record<string, string> = {
    OPENAI: 'gpt-4o-mini',
    ANTHROPIC: 'claude-sonnet-4-0',
    GOOGLE: 'gemini-2.0-flash',
    MISTRAL: 'mistral-small-latest',
    OPENROUTER: '',
};

export function DeckFormPage() {
    const {t} = useTranslation();
    const {deckId} = useParams();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const isEditing = !!deckId;

    const [form, setForm] = useState<DeckFormData>({name: ''});
    const [errors, setErrors] = useState<Partial<Record<keyof DeckFormData, string>>>({});

    const [fileModalOpen, setFileModalOpen] = useState(false);
    const [topicModalOpen, setTopicModalOpen] = useState(false);
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [aiProvider, setAiProvider] = useState('OPENAI');
    const [aiModel, setAiModel] = useState('');
    const [aiTopicPrompt, setAiTopicPrompt] = useState('');
    const [aiErrors, setAiErrors] = useState<Partial<Record<string, string>>>({});
    const [aiLoading, setAiLoading] = useState(false);

    // For edit mode, find the deck from cache or refetch
    const {data: existingDeck} = useQuery({
        queryKey: ['decks', 'edit', deckId],
        queryFn: async () => {
            // Try to find from cache first
            const cached = queryClient.getQueryData<{ content: DeckResponse[] }>(['decks', 'all', {
                pageSize: 15,
                direction: 'DESC'
            }]);
            const found = cached?.content.find((d) => d.id === Number(deckId));
            if (found) return found;
            // Otherwise fetch all and search
            const data = await getDecks({pageSize: 100});
            return data.content.find((d) => d.id === Number(deckId)) || null;
        },
        enabled: isEditing,
    });

    useEffect(() => {
        if (existingDeck) {
            setForm({name: existingDeck.name});
        }
    }, [existingDeck]);

    const createMutation = useMutation({
        mutationFn: (data: DeckFormData) => createDeck({name: sanitizeHtml(data.name)}),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: ['decks']});
            toast.success(t('toast:deckCreated'));
            navigate('/decks');
        },
    });

    const updateMutation = useMutation({
        mutationFn: (data: DeckFormData) => updateDeck(Number(deckId), {name: sanitizeHtml(data.name)}),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: ['decks']});
            toast.success(t('toast:deckUpdated'));
            navigate(`/decks/${deckId}`);
        },
    });

    const isPending = createMutation.isPending || updateMutation.isPending;

    const resetAiState = () => {
        setSelectedFile(null);
        setAiProvider('OPENAI');
        setAiModel('');
        setAiTopicPrompt('');
        setAiErrors({});
    };

    const handleFileGenerate = async (e: React.FormEvent) => {
        e.preventDefault();
        const errs: typeof aiErrors = {};
        if (!selectedFile) errs.file = t('ai:noFileSelected');
        if (aiProvider === 'OPENROUTER' && !aiModel.trim()) errs.model = t('ai:modelRequiredForOpenRouter');
        if (errs.file || errs.model) {
            setAiErrors(errs);
            return;
        }
        setAiLoading(true);
        try {
            const result = await generateDeckFromFile(selectedFile, form.name, aiProvider, aiModel || undefined);
            queryClient.invalidateQueries({queryKey: ['decks']});
            toast.success(t('ai:deckGenerated', {count: result.totalGenerated, skipped: result.totalSkipped}));
            setFileModalOpen(false);
            resetAiState();
            navigate(`/decks/${result.deck.id}`);
        } catch {
            toast.error(t('ai:generationFailed'));
        } finally {
            setAiLoading(false);
        }
    };

    const handleTopicGenerate = async (e: React.FormEvent) => {
        e.preventDefault();
        const parsed = aiTopicSchema.safeParse({
            prompt: aiTopicPrompt,
            provider: aiProvider,
            deckName: form.name,
            model: aiModel || undefined
        });
        if (!parsed.success) {
            setAiErrors(translateFieldErrors(t, parsed.error));
            return;
        }
        if (parsed.data.provider === 'OPENROUTER' && !parsed.data.model?.trim()) {
            setAiErrors({model: t('ai:modelRequiredForOpenRouter')});
            return;
        }
        setAiLoading(true);
        try {
            const result = await generateDeckFromTopic({
                prompt: sanitizeHtml(parsed.data.prompt),
                provider: parsed.data.provider as 'OPENAI' | 'ANTHROPIC' | 'GOOGLE' | 'MISTRAL' | 'OPENROUTER',
                deckName: sanitizeHtml(parsed.data.deckName),
                model: parsed.data.model || undefined,
            });
            queryClient.invalidateQueries({queryKey: ['decks']});
            toast.success(t('ai:deckGenerated', {count: result.totalGenerated, skipped: result.totalSkipped}));
            setTopicModalOpen(false);
            resetAiState();
            navigate(`/decks/${result.deck.id}`);
        } catch {
            toast.error(t('ai:generationFailed'));
        } finally {
            setAiLoading(false);
        }
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();

        const result = deckSchema.safeParse(form);
        if (!result.success) {
            setErrors(translateFieldErrors(t, result.error));
            return;
        }

        if (isEditing) {
            updateMutation.mutate(result.data);
        } else {
            createMutation.mutate(result.data);
        }
    };

    return (
        <div className="max-w-lg mx-auto">
            <h1 className="text-2xl font-bold text-slate-900 mb-6">
                {isEditing ? t('decks:editDeck') : t('decks:createNewDeck')}
            </h1>

            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-8">
                <form onSubmit={handleSubmit} className="space-y-6">
                    <Input
                        label={t('decks:deckName')}
                        placeholder={t('decks:deckNamePlaceholder')}
                        value={form.name}
                        onChange={(e) => {
                            setForm({name: e.target.value});
                            setErrors({});
                        }}
                        error={errors.name}
                        disabled={isPending}
                        autoFocus
                        maxLength={100}
                    />

                    <div className="flex gap-3">
                        <Button type="submit" isLoading={isPending} className="flex-1">
                            {isEditing ? t('common:saveChanges') : t('decks:createDeck')}
                        </Button>
                        <Button type="button" variant="secondary" onClick={() => navigate(-1)} disabled={isPending}>
                            {t('common:cancel')}
                        </Button>
                    </div>
                </form>

                {!isEditing && (
                    <div className="border-t border-slate-200 pt-6 mt-6">
                        <p className="text-sm font-medium text-slate-700 mb-3">{t('ai:orGenerateWithAI')}</p>
                        <div className="flex gap-3">
                            <Button type="button" variant="secondary" onClick={() => {
                                resetAiState();
                                setFileModalOpen(true);
                            }}>
                                ✨ {t('ai:generateFromFile')}
                            </Button>
                            <Button type="button" variant="secondary" onClick={() => {
                                resetAiState();
                                setTopicModalOpen(true);
                            }}>
                                📝 {t('ai:generateFromTopic')}
                            </Button>
                        </div>
                    </div>
                )}
            </div>

            {/* Generate from file modal */}
            <Modal isOpen={fileModalOpen} onClose={() => {
                setFileModalOpen(false);
                resetAiState();
            }} title={t('ai:generateFromFile')}>
                <form onSubmit={handleFileGenerate} className="space-y-4">
                    <div>
                        <label htmlFor="ai-file" className="block text-sm font-medium text-slate-700 mb-1">
                            {t('ai:selectFile')}
                        </label>
                        <input
                            id="ai-file"
                            type="file"
                            accept=".txt,.pdf"
                            onChange={(e) => {
                                setSelectedFile(e.target.files?.[0] ?? null);
                                setAiErrors((p) => ({...p, file: undefined}));
                            }}
                            disabled={aiLoading}
                            className="w-full text-sm text-slate-600 file:mr-3 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-medium file:bg-indigo-50 file:text-indigo-700 hover:file:bg-indigo-100 disabled:opacity-50"
                        />
                        {aiErrors.file && <p className="mt-1 text-xs text-red-600">{aiErrors.file}</p>}
                        <p className="text-xs text-slate-400 mt-1">{t('ai:fileHint')}</p>
                    </div>
                    <Select
                        label={t('ai:provider')}
                        value={aiProvider}
                        onChange={(e) => {
                            setAiProvider(e.target.value);
                            setAiModel('');
                            setAiErrors({});
                        }}
                        options={AI_PROVIDERS.map(p => ({value: p.value, label: t(p.labelKey)}))}
                        disabled={aiLoading}
                    />
                    {aiProvider === 'OPENROUTER' ? (
                        <Input
                            label={t('ai:model')}
                            placeholder="openai/gpt-4o-mini"
                            value={aiModel}
                            onChange={(e) => {
                                setAiModel(e.target.value);
                                setAiErrors((p) => ({...p, model: undefined}));
                            }}
                            error={aiErrors.model}
                            disabled={aiLoading}
                            required
                        />
                    ) : (
                        <p className="text-xs text-slate-500">
                            {t('ai:defaultModelHint', {
                                provider: t(`ai:provider${aiProvider.toLowerCase()}`),
                                model: MODEL_DEFAULTS[aiProvider]
                            })}
                        </p>
                    )}
                    <div className="flex justify-end gap-3 pt-2">
                        <Button type="button" variant="secondary" onClick={() => {
                            setFileModalOpen(false);
                            resetAiState();
                        }} disabled={aiLoading}>
                            {t('common:cancel')}
                        </Button>
                        <Button type="submit" isLoading={aiLoading}>
                            {t('ai:generate')}
                        </Button>
                    </div>
                </form>
            </Modal>

            {/* Generate from topic modal */}
            <Modal isOpen={topicModalOpen} onClose={() => {
                setTopicModalOpen(false);
                resetAiState();
            }} title={t('ai:generateFromTopic')}>
                <form onSubmit={handleTopicGenerate} className="space-y-4">
                    <div>
                        <label htmlFor="ai-prompt" className="block text-sm font-medium text-slate-700 mb-1">
                            {t('ai:topicPrompt')}
                        </label>
                        <textarea
                            id="ai-prompt"
                            placeholder={t('ai:topicPromptPlaceholder')}
                            value={aiTopicPrompt}
                            onChange={(e) => {
                                setAiTopicPrompt(e.target.value);
                                setAiErrors((p) => ({...p, prompt: undefined}));
                            }}
                            disabled={aiLoading}
                            maxLength={2000}
                            rows={5}
                            className={`w-full rounded-lg border px-3 py-2 text-sm text-slate-900 placeholder-slate-400 transition-colors focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 disabled:bg-slate-50 resize-y ${aiErrors.prompt ? 'border-red-500' : 'border-slate-300'}`}
                            aria-invalid={!!aiErrors.prompt}
                        />
                        {aiErrors.prompt && <p className="mt-1 text-xs text-red-600" role="alert">{aiErrors.prompt}</p>}
                        <p className="text-xs text-slate-400 mt-1">{aiTopicPrompt.length}/2000</p>
                    </div>
                    <Select
                        label={t('ai:provider')}
                        value={aiProvider}
                        onChange={(e) => {
                            setAiProvider(e.target.value);
                            setAiModel('');
                            setAiErrors({});
                        }}
                        options={AI_PROVIDERS.map(p => ({value: p.value, label: t(p.labelKey)}))}
                        disabled={aiLoading}
                    />
                    {aiProvider === 'OPENROUTER' ? (
                        <Input
                            label={t('ai:model')}
                            placeholder="openai/gpt-4o-mini"
                            value={aiModel}
                            onChange={(e) => {
                                setAiModel(e.target.value);
                                setAiErrors((p) => ({...p, model: undefined}));
                            }}
                            error={aiErrors.model}
                            disabled={aiLoading}
                            required
                        />
                    ) : (
                        <p className="text-xs text-slate-500">
                            {t('ai:defaultModelHint', {
                                provider: t(`ai:provider${aiProvider.toLowerCase()}`),
                                model: MODEL_DEFAULTS[aiProvider]
                            })}
                        </p>
                    )}
                    <div className="flex justify-end gap-3 pt-2">
                        <Button type="button" variant="secondary" onClick={() => {
                            setTopicModalOpen(false);
                            resetAiState();
                        }} disabled={aiLoading}>
                            {t('common:cancel')}
                        </Button>
                        <Button type="submit" isLoading={aiLoading}>
                            {t('ai:generate')}
                        </Button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
