package com.qapital.savings.rule;

import com.qapital.bankdata.transaction.Transaction;
import com.qapital.bankdata.transaction.TransactionsService;
import com.qapital.savings.event.SavingsEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StandardSavingsRulesService implements SavingsRulesService {

    private final TransactionsService transactionsService;

    @Autowired
    public StandardSavingsRulesService(TransactionsService transactionsService) {
        this.transactionsService = transactionsService;
    }

    @Override
    public List<SavingsRule> activeRulesForUser(Long userId) {

        SavingsRule guiltyPleasureRule = SavingsRule.createGuiltyPleasureRule(1l, userId, "Starbucks", 3.00d);
        guiltyPleasureRule.addSavingsGoal(1l);
        guiltyPleasureRule.addSavingsGoal(2l);
        SavingsRule roundupRule = SavingsRule.createRoundupRule(2l, userId, 2.00d);
        roundupRule.addSavingsGoal(1l);

        return List.of(guiltyPleasureRule, roundupRule);
    }

    @Override
    public List<SavingsEvent> executeRule(SavingsRule savingsRule) {

        List<SavingsEvent> savingsEvents = new ArrayList<>();
        List<Transaction> transactions = transactionsService.latestTransactionsForUser(savingsRule.getId());

        for (Transaction transaction : transactions) {

            // execute only on expense transactions
            if (transaction.getAmount() < 0) {
                continue;
            }
            if (savingsRule.getRuleType().equals(SavingsRule.RuleType.roundup)) {

                savingsEvents.addAll(getSavingsEventsIfRoundingRule(savingsRule, transaction));

            } else if (savingsRule.getRuleType().equals(SavingsRule.RuleType.guiltypleasure)) {
                savingsEvents.addAll(getSavingsEventsIfGuiltyPleasure(savingsRule, transaction));
            }
        }
        return savingsEvents;
    }

    private List<SavingsEvent> getSavingsEventsIfGuiltyPleasure(SavingsRule savingsRule, Transaction transaction) {
        if (savingsRule.getPlaceDescription().equals(transaction.getDescription())) {
            double savingsAmount = savingsRule.getAmount();
            return createSavingsEvent(savingsRule, savingsAmount);
        }
        return Collections.emptyList();
    }

    private List<SavingsEvent> getSavingsEventsIfRoundingRule(SavingsRule savingsRule, Transaction transaction) {
        double savingsAmount;
        double roundUpAmount = savingsRule.getAmount();
        double amount = Math.abs(transaction.getAmount());

        while (roundUpAmount-amount < 0){
            roundUpAmount = roundUpAmount + savingsRule.getAmount();
        }
        
        savingsAmount = roundUpAmount - amount;

        return createSavingsEvent(savingsRule, savingsAmount);

    }

    private List<SavingsEvent> createSavingsEvent(SavingsRule savingsRule, double savingsAmount){
        int nrOfGoals = savingsRule.getSavingsGoalIds().size();
        return savingsRule.getSavingsGoalIds().stream()
                .map(sgId -> new SavingsEvent(savingsRule.getUserId(),
                        sgId,
                        savingsRule.getId(),
                        SavingsEvent.EventName.rule_application,
                        LocalDate.now(),
                        savingsAmount/nrOfGoals,
                        null,
                        savingsRule)).collect(Collectors.toList());
    }
}
