import {Modal} from '@/components/ui/Modal';
import {Button} from '@/components/ui/Button';
import {useTranslation} from 'react-i18next';

interface ConfirmDialogProps {
    isOpen: boolean;
    onClose: () => void;
    onConfirm: () => void;
    title: string;
    message: string;
    confirmLabel?: string;
    isLoading?: boolean;
}

export function ConfirmDialog({
                                  isOpen,
                                  onClose,
                                  onConfirm,
                                  title,
                                  message,
                                  confirmLabel,
                                  isLoading,
                              }: ConfirmDialogProps) {
    const {t} = useTranslation();
    return (
        <Modal isOpen={isOpen} onClose={onClose} title={title}>
            <p className="text-sm text-slate-600 mb-6">{message}</p>
            <div className="flex justify-end gap-3">
                <Button variant="secondary" onClick={onClose} disabled={isLoading}>
                    {t('common:cancel')}
                </Button>
                <Button variant="danger" onClick={onConfirm} isLoading={isLoading}>
                    {confirmLabel || t('common:delete')}
                </Button>
            </div>
        </Modal>
    );
}
