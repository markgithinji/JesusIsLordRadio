package com.smoothradio.jesusislordradio;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.snackbar.Snackbar;

import java.util.Calendar;

public class SetTimerDialog extends DialogFragment implements TimePickerDialog.OnTimeSetListener {
    MainActivity mainActivity;
    Calendar calendar;
   public ConstraintLayout constraintLayout;
    Intent intent = new Intent();

    SetTimerDialog(ConstraintLayout constraintLayout)
    {
        this.constraintLayout = constraintLayout;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mainActivity=(MainActivity) getActivity();
        calendar = Calendar.getInstance();
        int hour =calendar.get(Calendar.HOUR_OF_DAY);
        int minute=calendar.get(Calendar.MINUTE);
        return new TimePickerDialog(getActivity(),this,hour,minute, DateFormat.is24HourFormat(getActivity()));
    }

    @Override
    public void onTimeSet(TimePicker timePicker, int i, int i1) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            calendar.set(Calendar.HOUR_OF_DAY,i);
            calendar.set(Calendar.MINUTE,i1);
            calendar.set(Calendar.SECOND,0);
            Long timeInMillis=calendar.getTimeInMillis();
            intent.putExtra("timeInMillis",timeInMillis);
            intent.setAction("JILSetTimer");
            intent.setPackage(mainActivity.getPackageName());
            getActivity().sendBroadcast(intent);
            String waitTime = java.text.DateFormat.getTimeInstance().format(calendar.getTime());
            Snackbar.make(constraintLayout,"radio will stop at "+waitTime,Snackbar.LENGTH_LONG).show();
        }
        else
        {
            Toast.makeText(mainActivity, "Unsupported Android Version", Toast.LENGTH_SHORT).show();
        }

    }
}
