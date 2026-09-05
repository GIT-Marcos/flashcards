import {useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {useInfiniteQuery, useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {deleteDeck, getDecks} from '@/api/decks.api';
import {createCard, deleteCard, getDeckCards, getPendingCards, updateCard} from '@/api/cards.api';
import {generateCardsFromFile} from '@/api/ai.api';
import {Button} from '@/components/ui/Button';
import {Input} from '@/components/ui/Input';
import {Select} from '@/components/ui/Select';
import {Modal} from '@/components/ui/Modal';
import {Badge} from '@/components/ui/Badge';
import {SkeletonList} from '@/components/ui/Skeleton';
import {EmptyState} from '@/components/shared/EmptyState';
import {ErrorState} from '@/components/shared/ErrorState';
import {CardItem} from '@/components/shared/CardItem';
import {PaginationLoader} from '@/components/shared/PaginationLoader';
import {ConfirmDialog} from '@/components/shared/ConfirmDialog';
import {type CardFormData, cardSchema, translateFieldErrors} from '@/lib/validators';
import {sanitizeHtml} from '@/lib/sanitize';
import {DEFAULT_PAGE_SIZE} from '@/lib/constants';
import {toast} from 'sonner';
import {useTranslation} from 'react-i18next';
import type {CardResponse} from '@/types/card.types';
import type {DeckResponse} from '@/types/deck.types';
import type {PaginationParams} from '@/types/api.types';

const AI_PROVIDERS_DETAIL: Array<{ value: string; labelKey: string }> = [
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

type TabType = 'all' | 'pending';

export function DeckDetailPage() {
    const {t} = useTranslation();
    const {deckId} = useParams();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const id = Number(deckId);

    const [activeTab, setActiveTab] = useState<TabType>('all');
    const [cardModalOpen, setCardModalOpen] = useState(false);
    const [editingCard, setEditingCard] = useState<CardResponse | null>(null);
    const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
    const [cardDeleteId, setCardDeleteId] = useState<number | null>(null);

    // Card form state
    const [cardForm, setCardForm] = useState<CardFormData>({front: '', back: ''});
    const [cardErrors, setCardErrors] = useState<Partial<Record<keyof CardFormData, string>>>({});

    const [aiModalOpen, setAiModalOpen] = useState(false);
    const [aiFile, setAiFile] = useState<File | null>(null);
    const [aiProvider, setAiProvider] = useState('OPENAI');
    const [aiModel, setAiModel] = useState('');
    const [aiFormErrors, setAiFormErrors] = useState<Partial<Record<'file' | 'model', string>>>({});

    // Deck info
    const deckQuery = useQuery({
        queryKey: ['decks', 'detail', id],
        queryFn: async () => {
            const data = await getDecks({pageSize: 100});
            return data.content.find((d) => d.id === id) || null;
        },
    });

    // All cards — infinite query
    const allCardsInfinite = useInfiniteQuery({
        queryKey: ['cards', id, 'all'],
        queryFn: ({pageParam}) => getDeckCards(id, pageParam),
        initialPageParam: {pageSize: DEFAULT_PAGE_SIZE, direction: 'ASC'} as PaginationParams,
        getNextPageParam: (lastPage) => {
            if (!lastPage.hasNext) return undefined;
            const content = lastPage.content;
            if (content.length === 0) return undefined;
            const last = content[content.length - 1] as CardResponse;
            return {
                lastId: last.id,
                cursorValue: last.nextReviewDate,
                pageSize: DEFAULT_PAGE_SIZE,
                direction: 'ASC',
            } as PaginationParams;
        },
        enabled: activeTab === 'all',
    });

    // Pending cards — infinite query
    const pendingCardsInfinite = useInfiniteQuery({
        queryKey: ['cards', id, 'pending'],
        queryFn: ({pageParam}) => getPendingCards(id, pageParam),
        initialPageParam: {pageSize: DEFAULT_PAGE_SIZE, direction: 'ASC'} as PaginationParams,
        getNextPageParam: (lastPage) => {
            if (!lastPage.hasNext) return undefined;
            const content = lastPage.content;
            if (content.length === 0) return undefined;
            const last = content[content.length - 1] as CardResponse;
            return {
                lastId: last.id,
                cursorValue: last.nextReviewDate,
                pageSize: DEFAULT_PAGE_SIZE,
                direction: 'ASC',
            } as PaginationParams;
        },
        enabled: activeTab === 'pending',
    });

    const activeQuery = activeTab === 'all' ? allCardsInfinite : pendingCardsInfinite;
    const currentCards = activeQuery.data?.pages.flatMap((page) => page.content) ?? [];

    // Mutations
    const createCardMutation = useMutation({
        mutationFn: (data: CardFormData) =>
            createCard(id, {front: sanitizeHtml(data.front), back: sanitizeHtml(data.back)}),
        onSuccess: () => {
            toast.success(t('toast:cardCreated'));
            setCardModalOpen(false);
            resetCardForm();
            queryClient.invalidateQueries({queryKey: ['cards', id]});
            queryClient.invalidateQueries({queryKey: ['decks']});
        },
    });

    const updateCardMutation = useMutation({
        mutationFn: ({cardId, data}: { cardId: number; data: CardFormData }) =>
            updateCard(cardId, {front: sanitizeHtml(data.front), back: sanitizeHtml(data.back)}),
        onSuccess: () => {
            toast.success(t('toast:cardUpdated'));
            setCardModalOpen(false);
            setEditingCard(null);
            resetCardForm();
            queryClient.invalidateQueries({queryKey: ['cards', id]});
        },
    });

    const deleteCardMutation = useMutation({
        mutationFn: (cardId: number) => deleteCard(cardId),
        onSuccess: () => {
            toast.success(t('toast:cardDeleted'));
            setCardDeleteId(null);
            queryClient.invalidateQueries({queryKey: ['cards', id]});
            queryClient.invalidateQueries({queryKey: ['decks']});
        },
    });

    const deleteDeckMutation = useMutation({
        mutationFn: () => deleteDeck(id),
        onSuccess: () => {
            toast.success(t('toast:deckDeleted'));
            queryClient.invalidateQueries({queryKey: ['decks']});
            navigate('/decks', {replace: true});
        },
    });

    const generateAiMutation = useMutation({
        mutationFn: () => generateCardsFromFile(id, aiFile!, aiProvider, aiModel || undefined),
        onSuccess: (result) => {
            toast.success(t('ai:cardsGenerated', {count: result.totalGenerated, skipped: result.totalSkipped}));
            setAiModalOpen(false);
            setAiFile(null);
            setAiProvider('OPENAI');
            setAiModel('');
            setAiFormErrors({});
            queryClient.invalidateQueries({queryKey: ['cards', id]});
            queryClient.invalidateQueries({queryKey: ['decks']});
        },
        onError: () => {
            toast.error(t('ai:generationFailed'));
        },
    });

    const resetCardForm = () => {
        setCardForm({front: '', back: ''});
        setCardErrors({});
    };

    const openCreateCard = () => {
        setEditingCard(null);
        resetCardForm();
        setCardModalOpen(true);
    };

    const openEditCard = (card: CardResponse) => {
        setEditingCard(card);
        setCardForm({front: card.front, back: card.back});
        setCardErrors({});
        setCardModalOpen(true);
    };

    const handleCardSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        const result = cardSchema.safeParse(cardForm);
        if (!result.success) {
            setCardErrors(translateFieldErrors(t, result.error));
            return;
        }

        if (editingCard) {
            updateCardMutation.mutate({cardId: editingCard.id, data: result.data});
        } else {
            createCardMutation.mutate(result.data);
        }
    };

    const isCardMutating = createCardMutation.isPending || updateCardMutation.isPending;
    const deck: DeckResponse | null | undefined = deckQuery.data;

    return (
        <div>
            {/* Deck header */}
            <div className="flex items-start justify-between mb-6 flex-wrap gap-4">
                <div>
                    <div className="flex items-center gap-3 mb-1">
                        <button
                            onClick={() => navigate('/decks')}
                            className="text-slate-400 hover:text-slate-600 transition-colors"
                            aria-label={t('decks:backToDecks')}
                        >
                            ←
                        </button>
                        <h1 className="text-2xl font-bold text-slate-900">
                            {deck?.name || t('common:loading')}
                        </h1>
                        {deck?.hasPendingCards && <Badge variant="warning">{t('decks:due')}</Badge>}
                    </div>
                </div>
                <div className="flex gap-2">
                    {deck?.hasPendingCards && (
                        <Button onClick={() => navigate(`/decks/${id}/study`)}>
                            {t('decks:studyNow')}
                        </Button>
                    )}
                    <Button variant="secondary" onClick={() => navigate(`/decks/${id}/edit`)}>
                        {t('common:edit')}
                    </Button>
                    <Button variant="danger" onClick={() => setDeleteConfirmOpen(true)}>
                        {t('common:delete')}
                    </Button>
                </div>
            </div>

            {/* Tabs */}
            <div className="flex gap-1 mb-6 border-b border-slate-200">
                {(['all', 'pending'] as TabType[]).map((tab) => (
                    <button
                        key={tab}
                        onClick={() => setActiveTab(tab)}
                        className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                            activeTab === tab
                                ? 'border-indigo-600 text-indigo-600'
                                : 'border-transparent text-slate-500 hover:text-slate-700'
                        }`}
                    >
                        {tab === 'all' ? t('decks:allCards') : t('decks:pending')}
                    </button>
                ))}
            </div>

            {/* Add card button */}
            <div className="flex justify-end mb-4 gap-2">
                <Button size="sm" variant="secondary" onClick={() => setAiModalOpen(true)}>
                    ✨ {t('ai:generateCards')}
                </Button>
                <Button size="sm" onClick={openCreateCard}>
                    {t('decks:addCard')}
                </Button>
            </div>

            {/* Card list */}
            {activeQuery.isLoading && <SkeletonList count={5}/>}

            {activeQuery.isError && !activeQuery.data && (
                <ErrorState onRetry={() => activeQuery.refetch()}/>
            )}

            {!activeQuery.isLoading && currentCards.length === 0 && !activeQuery.isError && (
                <EmptyState
                    icon={activeTab === 'all' ? '🃏' : '✅'}
                    title={activeTab === 'all' ? t('empty:noCardsYet') : t('empty:noPendingCards')}
                    description={
                        activeTab === 'all'
                            ? t('empty:noCardsDesc')
                            : t('empty:noPendingCardsDesc')
                    }
                    actionLabel={activeTab === 'all' ? t('empty:addFirstCard') : undefined}
                    onAction={activeTab === 'all' ? openCreateCard : undefined}
                />
            )}

            {currentCards.length > 0 && (
                <>
                    <div className="space-y-3">
                        {currentCards.map((card) => (
                            <div key={card.id} className="group relative">
                                <CardItem card={card} onClick={() => openEditCard(card)}/>
                                <button
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        setCardDeleteId(card.id);
                                    }}
                                    className="absolute top-3 right-3 opacity-0 group-hover:opacity-100 transition-opacity p-1.5 rounded-lg hover:bg-red-50 text-slate-400 hover:text-red-500"
                                    aria-label={t('decks:deleteCard')}
                                >
                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"
                                         strokeWidth={2}>
                                        <path strokeLinecap="round" strokeLinejoin="round"
                                              d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                                    </svg>
                                </button>
                            </div>
                        ))}
                    </div>
                    <PaginationLoader
                        hasNext={activeQuery.hasNextPage}
                        isLoading={activeQuery.isFetchingNextPage}
                        onLoadMore={() => activeQuery.fetchNextPage()}
                    />
                </>
            )}

            {/* Generate with AI Modal */}
            <Modal
                isOpen={aiModalOpen}
                onClose={() => {
                    setAiModalOpen(false);
                    setAiFile(null);
                    setAiProvider('OPENAI');
                    setAiModel('');
                    setAiFormErrors({});
                }}
                title={t('ai:generateCards')}
            >
                <form
                    onSubmit={(e) => {
                        e.preventDefault();
                        const errors: typeof aiFormErrors = {};
                        if (!aiFile) errors.file = t('ai:noFileSelected');
                        if (aiProvider === 'OPENROUTER' && !aiModel.trim()) errors.model = t('ai:modelRequiredForOpenRouter');
                        if (errors.file || errors.model) {
                            setAiFormErrors(errors);
                            return;
                        }
                        setAiFormErrors({});
                        generateAiMutation.mutate();
                    }}
                    className="space-y-4"
                >
                    <div>
                        <label htmlFor="ai-cards-file" className="block text-sm font-medium text-slate-700 mb-1">
                            {t('ai:selectFile')}
                        </label>
                        <input
                            id="ai-cards-file"
                            type="file"
                            accept=".txt,.pdf"
                            onChange={(e) => {
                                setAiFile(e.target.files?.[0] ?? null);
                                setAiFormErrors((p) => ({...p, file: undefined}));
                            }}
                            disabled={generateAiMutation.isPending}
                            className="w-full text-sm text-slate-600 file:mr-3 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-medium file:bg-indigo-50 file:text-indigo-700 hover:file:bg-indigo-100 disabled:opacity-50"
                        />
                        {aiFormErrors.file && <p className="mt-1 text-xs text-red-600">{aiFormErrors.file}</p>}
                        <p className="text-xs text-slate-400 mt-1">{t('ai:fileHint')}</p>
                    </div>
                    <Select
                        label={t('ai:provider')}
                        value={aiProvider}
                        onChange={(e) => {
                            setAiProvider(e.target.value);
                            setAiModel('');
                            setAiFormErrors({});
                        }}
                        options={AI_PROVIDERS_DETAIL.map(p => ({value: p.value, label: t(p.labelKey)}))}
                        disabled={generateAiMutation.isPending}
                    />
                    {aiProvider === 'OPENROUTER' ? (
                        <Input
                            label={t('ai:model')}
                            placeholder="openai/gpt-4o-mini"
                            value={aiModel}
                            onChange={(e) => {
                                setAiModel(e.target.value);
                                setAiFormErrors((p) => ({...p, model: undefined}));
                            }}
                            error={aiFormErrors.model}
                            disabled={generateAiMutation.isPending}
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
                        <Button
                            type="button"
                            variant="secondary"
                            onClick={() => {
                                setAiModalOpen(false);
                                setAiFile(null);
                                setAiProvider('OPENAI');
                                setAiModel('');
                                setAiFormErrors({});
                            }}
                            disabled={generateAiMutation.isPending}
                        >
                            {t('common:cancel')}
                        </Button>
                        <Button type="submit" isLoading={generateAiMutation.isPending}>
                            {t('ai:generate')}
                        </Button>
                    </div>
                </form>
            </Modal>

            {/* Card Modal */}
            <Modal
                isOpen={cardModalOpen}
                onClose={() => {
                    setCardModalOpen(false);
                    setEditingCard(null);
                    resetCardForm();
                }}
                title={editingCard ? t('decks:editCard') : t('decks:addCardModal')}
            >
                <form onSubmit={handleCardSubmit} className="space-y-4">
                    <Input
                        label={t('decks:front')}
                        placeholder={t('decks:questionOrTerm')}
                        value={cardForm.front}
                        onChange={(e) => {
                            setCardForm((prev) => ({...prev, front: e.target.value}));
                            setCardErrors((prev) => ({...prev, front: undefined}));
                        }}
                        error={cardErrors.front}
                        disabled={isCardMutating}
                        maxLength={255}
                        autoFocus
                    />
                    <div className="w-full">
                        <label htmlFor="card-back" className="block text-sm font-medium text-slate-700 mb-1">
                            {t('decks:back')}
                        </label>
                        <textarea
                            id="card-back"
                            placeholder={t('decks:answerOrDefinition')}
                            value={cardForm.back}
                            onChange={(e) => {
                                setCardForm((prev) => ({...prev, back: e.target.value}));
                                setCardErrors((prev) => ({...prev, back: undefined}));
                            }}
                            disabled={isCardMutating}
                            maxLength={5000}
                            rows={5}
                            className={`w-full rounded-lg border px-3 py-2 text-sm text-slate-900 placeholder-slate-400 transition-colors focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 disabled:bg-slate-50 resize-y ${
                                cardErrors.back ? 'border-red-500' : 'border-slate-300'
                            }`}
                            aria-invalid={!!cardErrors.back}
                        />
                        {cardErrors.back && (
                            <p className="mt-1 text-xs text-red-600" role="alert">{cardErrors.back}</p>
                        )}
                    </div>
                    <div className="flex gap-3 justify-end">
                        <Button
                            type="button"
                            variant="secondary"
                            onClick={() => {
                                setCardModalOpen(false);
                                setEditingCard(null);
                                resetCardForm();
                            }}
                            disabled={isCardMutating}
                        >
                            {t('common:cancel')}
                        </Button>
                        <Button type="submit" isLoading={isCardMutating}>
                            {editingCard ? t('common:save') : t('decks:addCard')}
                        </Button>
                    </div>
                </form>
            </Modal>

            {/* Delete Deck Confirm */}
            <ConfirmDialog
                isOpen={deleteConfirmOpen}
                onClose={() => setDeleteConfirmOpen(false)}
                onConfirm={() => deleteDeckMutation.mutate()}
                title={t('decks:deleteDeck')}
                message={t('decks:deleteDeckConfirm')}
                confirmLabel={t('decks:deleteDeck')}
                isLoading={deleteDeckMutation.isPending}
            />

            {/* Delete Card Confirm */}
            <ConfirmDialog
                isOpen={cardDeleteId !== null}
                onClose={() => setCardDeleteId(null)}
                onConfirm={() => {
                    if (cardDeleteId !== null) deleteCardMutation.mutate(cardDeleteId);
                }}
                title={t('decks:deleteCard')}
                message={t('decks:deleteCardConfirm')}
                confirmLabel={t('decks:deleteCard')}
                isLoading={deleteCardMutation.isPending}
            />
        </div>
    );
}
